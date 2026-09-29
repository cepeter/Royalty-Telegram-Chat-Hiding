package io.github.cepeter.royalty;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import io.github.cepeter.royalty.core.BackupCodec;
import io.github.cepeter.royalty.core.BackupData;
import io.github.cepeter.royalty.core.BackupOperation;
import io.github.cepeter.royalty.core.BackupPreview;
import io.github.cepeter.royalty.core.ProtectedModalController;
import io.github.cepeter.royalty.core.SettingsAccess;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SAF and dialog adapter; crypto and import decisions live in independently tested core classes. */
final class BackupController {
    static final int CREATE_REQUEST = 91, OPEN_REQUEST = 92;
    private final MainActivity activity;
    private final SettingsAccess access;
    private final ProtectedModalController modals;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private BackupOperation pendingOperation, runningOperation;
    private BackupData pendingPreview;
    private Uri pendingImportUri;
    private boolean destroyed, busy;
    private boolean exportPending;

    BackupController(MainActivity activity, SettingsAccess access, ProtectedModalController modals) {
        this.activity = activity; this.access = access; this.modals = modals;
    }

    void exportSaved() {
        if (!access.allowed() || busy || !activity.backupDraft().initialized()) return;
        passphraseDialog(true, pass -> {
            pendingOperation = new BackupOperation(pass);
            exportPending = true;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, "royalty-config.rybk");
            launch(intent, CREATE_REQUEST);
        });
    }

    void importFile() {
        if (!access.allowed() || busy || !activity.backupDraft().initialized()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        launch(intent, OPEN_REQUEST);
    }

    private void launch(Intent intent, int code) {
        try { activity.startActivityForResult(intent, code); }
        catch (RuntimeException error) { clearPending(); exportPending = false; notice("Document picker unavailable"); }
    }

    void onDocument(int code, int result, Intent intent) {
        if (destroyed) return;
        if (result != MainActivity.RESULT_OK || intent == null || intent.getData() == null) {
            clearPending(); exportPending = false; return;
        }
        Uri uri = intent.getData();
        if (code == CREATE_REQUEST) {
            if (!exportPending || pendingOperation == null) return;
            exportPending = false;
            BackupOperation operation = takePending();
            // Snapshot only the confirmed configuration before dispatching the worker.
            BackupData.Export snapshot;
            try { snapshot = BackupData.exportFromSaved(activity.backupDraft().baseline()); }
            catch (RuntimeException error) { operation.cancel(); notice("Saved configuration cannot be exported"); return; }
            runWorker(operation, () -> {
                byte[] bytes = null;
                try {
                    operation.checkActive();
                    bytes = BackupCodec.encrypt(snapshot.data(), operation.passphrase());
                    operation.checkActive();
                    try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new IOException("document cannot be opened");
                        operation.track(out);
                        try {
                            for (int offset = 0; offset < bytes.length; offset += 8192) {
                                operation.checkActive();
                                out.write(bytes, offset, Math.min(8192, bytes.length - offset));
                            }
                            operation.checkActive();
                            out.flush();
                        } finally { operation.untrack(out); }
                    }
                    callback(() -> { if (!operation.cancelled()) notice("Encrypted configuration exported; "
                            + snapshot.skippedUnbound() + " legacy unbound selection(s) omitted"); });
                } catch (Exception error) { callback(() -> { if (!operation.cancelled()) notice("Export failed; discard the incomplete document"); }); }
                finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
            });
        } else if (code == OPEN_REQUEST) {
            pendingImportUri = uri;
            resumeIfAllowed();
        }
    }

    private void promptImport(Uri uri) {
        passphraseDialog(false, pass -> {
            BackupOperation operation = new BackupOperation(pass);
            runWorker(operation, () -> {
                byte[] bytes = null;
                try {
                    bytes = readBounded(uri, operation);
                    operation.checkActive();
                    BackupData data = BackupCodec.decrypt(bytes, operation.passphrase());
                    operation.checkActive();
                    callback(() -> { if (!operation.cancelled()) { pendingPreview = data; resumeIfAllowed(); } });
                } catch (Exception error) { callback(() -> { if (!operation.cancelled()) notice("Import failed: wrong passphrase or invalid backup"); }); }
                finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
            });
        });
    }

    private byte[] readBounded(Uri uri, BackupOperation operation) throws IOException {
        operation.checkActive();
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("document cannot be opened");
            operation.track(in);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while (true) {
                    operation.checkActive();
                    count = in.read(buffer);
                    if (count == -1) break;
                    if (out.size() + count > BackupCodec.MAX_BYTES) throw new IOException("backup too large");
                    out.write(buffer, 0, count);
                }
                operation.checkActive();
                return out.toByteArray();
            } finally { operation.untrack(in); }
        }
    }

    private interface PassAction { void accept(char[] pass); }
    private void passphraseDialog(boolean export, PassAction action) {
        if (!access.allowed() || destroyed) return;
        EditText input = passwordInput("Passphrase");
        EditText confirm = export ? passwordInput("Confirm passphrase") : null;
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(24, 8, 24, 8);
        fields.addView(input);
        if (confirm != null) fields.addView(confirm);
        Object token = new Object();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(export ? "Export saved configuration" : "Unlock encrypted backup")
                .setMessage(export ? "Exports confirmed saved bindings and non-authentication preferences. "
                        + activity.backupDraft().baseline().unbound().size()
                        + " saved legacy unbound selection(s) will be omitted. Bind and Save them before relying on this backup. Use at least 12 characters."
                        : "Enter the backup passphrase.")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(export ? "Choose document" : "Open", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!access.allowed()) { dialog.dismiss(); return; }
            char[] pass = copyChars(input);
            char[] check = confirm == null ? null : copyChars(confirm);
            boolean valid = pass.length > 0 && (!export || (pass.length >= 12 && Arrays.equals(pass, check)));
            if (check != null) Arrays.fill(check, '\0');
            if (!valid) { Arrays.fill(pass, '\0'); notice(export ? "Use matching passphrases of at least 12 characters" : "Enter the passphrase"); return; }
            if (!modals.consume(token)) { Arrays.fill(pass, '\0'); dialog.dismiss(); return; }
            input.getText().clear(); if (confirm != null) confirm.getText().clear();
            dialog.dismiss();
            action.accept(pass);
        }));
        dialog.setOnDismissListener(ignored -> {
            input.getText().clear(); if (confirm != null) confirm.getText().clear();
            modals.closed(token);
        });
        dialog.show();
        modals.open(token, dialog::dismiss);
    }

    private EditText passwordInput(String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSaveEnabled(false);
        input.setSaveFromParentEnabled(false);
        return input;
    }
    private static char[] copyChars(EditText input) {
        int length = input.length();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) chars[i] = input.getText().charAt(i);
        return chars;
    }

    void resumeIfAllowed() {
        if (destroyed || !access.allowed()) return;
        if (pendingImportUri != null) {
            Uri uri = pendingImportUri;
            pendingImportUri = null;
            promptImport(uri);
            return;
        }
        if (pendingPreview == null) return;
        BackupData source = pendingPreview;
        pendingPreview = null;
        BackupPreview preview = BackupPreview.create(source, activity.backupDraft().current(), activity.backupInventory());
        String message = "Recognized owners: " + preview.recognizedOwners() + "; unavailable/ambiguous owners: " + preview.skippedOwners()
                + "\nAdd: " + preview.added() + "; already selected: " + preview.already()
                + "; unknown: " + preview.skipped() + "; ambiguous: " + preview.ambiguous()
                + "; conflicts or legacy unbound: " + preview.conflicts()
                + "\nPreference changes: " + preview.preferenceChanges()
                + "\nAuthentication stays unchanged. Existing selections remain. Apply edits the draft; Save commits them.";
        Object token = new Object();
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Review import")
                .setMessage(message).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Apply to draft", (ignored, which) -> {
                    if (!modals.consume(token)) return;
                    if (!preview.apply(activity.backupDraft(), activity.refreshBackupInventory())) {
                        notice("Accounts or draft changed. Reopen the backup and review again."); return;
                    }
                    activity.renderBackupDraft();
                    notice("Import applied to draft; tap Save to keep it");
                }).create();
        dialog.setOnDismissListener(ignored -> modals.closed(token));
        dialog.show();
        modals.open(token, dialog::dismiss);
    }

    private void runWorker(BackupOperation operation, Runnable work) {
        if (busy || destroyed) { operation.cancel(); return; }
        busy = true;
        runningOperation = operation;
        try {
            worker.execute(() -> {
                try { operation.checkActive(); work.run(); }
                catch (java.util.concurrent.CancellationException ignored) { }
                finally {
                    operation.clearSecret();
                    callback(() -> { if (runningOperation == operation) runningOperation = null; busy = false; });
                }
            });
        } catch (RuntimeException error) {
            operation.cancel(); runningOperation = null; busy = false;
        }
    }
    private void callback(Runnable action) { activity.runOnUiThread(() -> { if (!destroyed) action.run(); }); }
    private void notice(String message) { if (!destroyed) Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
    private BackupOperation takePending() { BackupOperation operation = pendingOperation; pendingOperation = null; return operation; }
    private void clearPending() { if (pendingOperation != null) pendingOperation.cancel(); pendingOperation = null; }
    void destroy() {
        destroyed = true;
        clearPending();
        if (runningOperation != null) runningOperation.cancel();
        pendingPreview = null; pendingImportUri = null;
        worker.shutdownNow();
    }
}
