package io.github.cepeter.royalty;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import io.github.cepeter.royalty.catalog.CatalogRepository;
import io.github.cepeter.royalty.catalog.CatalogRequestClient;
import io.github.cepeter.royalty.catalog.CatalogUpdates;
import io.github.cepeter.royalty.catalog.PendingRequestStore;
import io.github.cepeter.royalty.config.ConfigStore;
import io.github.cepeter.royalty.config.XposedPreferenceService;
import io.github.cepeter.royalty.core.CatalogEntry;
import io.github.cepeter.royalty.core.CatalogSelection;
import io.github.cepeter.royalty.core.AccountInventory;
import io.github.cepeter.royalty.core.AccountBindingPresentation;
import io.github.cepeter.royalty.core.ConfigurationDraft;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.DiagnosticsFormatter;
import io.github.cepeter.royalty.core.HiddenConfig;
import io.github.cepeter.royalty.core.ProtectionStatus;
import io.github.cepeter.royalty.update.UpdateChecker;
import io.github.cepeter.royalty.update.UpdateRelease;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final long UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1_000;
    private static final String UPDATE_PREFERENCES = "update_checks";
    private static final String LAST_UPDATE_CHECK = "last_update_check";
    private static final String DISMISSED_UPDATE_TAG = "dismissed_update_tag";
    private static final String CACHED_UPDATE_TAG = "cached_update_tag";
    private static final String CACHED_UPDATE_URL = "cached_update_url";

    private final List<CatalogEntry> catalog = new ArrayList<>();
    private final List<CatalogEntry> visibleCatalog = new ArrayList<>();
    private static final String DRAFT_STATE = "configuration_draft";
    private ConfigurationDraft draft = new ConfigurationDraft();
    private boolean preferencesAvailable;
    private boolean renderingSettings;
    private boolean catalogRequestTimedOut;
    private String catalogError = "";
    private String requestNonce;
    private long requestExpiresAt;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final XposedPreferenceService.Listener preferenceListener = current ->
            mainHandler.post(() -> {
                preferences = current;
                renderCached();
            });
    private final Runnable catalogTimeout = () -> {
        if (requestNonce != null && requestExpiresAt != 0
                && SystemClock.elapsedRealtime() >= requestExpiresAt) {
            catalogRequestTimedOut = true;
            catalogError = "Catalog response timed out";
            CatalogUpdates.shared().complete(requestNonce, false, catalogError);
            requestNonce = null;
            requestExpiresAt = 0;
            renderCatalogAndHealth();
        }
    };
    private final CatalogUpdates.Listener catalogUpdateListener = event -> mainHandler.post(() -> {
        if (!event.nonce().equals(requestNonce)) return;
        catalogRequestTimedOut = !event.success();
        catalogError = event.detail();
        requestNonce = null;
        requestExpiresAt = 0;
        mainHandler.removeCallbacks(catalogTimeout);
        renderCatalogAndHealth();
    });

    private CatalogRepository catalogRepository;
    private UpdateChecker updateChecker;
    private SharedPreferences preferences;
    private LinearLayout updateCard;
    private TextView updateMessage;
    private UpdateRelease availableUpdate;
    private TextView frameworkStatusDot;
    private TextView frameworkStatusText;
    private TextView telegramStatusDot;
    private TextView telegramStatusText;
    private TextView protectionDetails;
    private EditText searchInput;
    private ListView dialogList;
    private ArrayAdapter<String> dialogAdapter;
    private Switch notificationSwitch;
    private Switch premiumSwitch;
    private Button saveButton;
    private CatalogSelectionControls selectionControls;
    private AccountInventory accountInventory = AccountInventory.incomplete("no catalog");
    private final Runnable freshnessTick = new Runnable() {
        @Override public void run() {
            if (catalogRepository != null && protectionDetails != null)
                updateConnectionStatus(catalogRepository.loadHookStatuses());
            mainHandler.postDelayed(this, 15_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof ConfigurationDraft) {
            draft = (ConfigurationDraft) retained;
        } else if (savedInstanceState != null) {
            Object saved = savedInstanceState.getSerializable(DRAFT_STATE);
            if (saved instanceof ConfigurationDraft.State)
                draft = ConfigurationDraft.restore((ConfigurationDraft.State) saved);
        }
        catalogRepository = new CatalogRepository(this);
        updateChecker = new UpdateChecker(mainHandler);
        setContentView(buildContentView());
        renderCached();
        XposedPreferenceService.subscribe(preferenceListener);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return draft;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(DRAFT_STATE, draft.snapshot());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (updateChecker != null) {
            updateChecker.close();
        }
        XposedPreferenceService.unsubscribe(preferenceListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CatalogUpdates.shared().subscribe(catalogUpdateListener);
        renderCached();
        requestCatalog();
        maybeCheckForUpdate();
        mainHandler.removeCallbacks(freshnessTick);
        mainHandler.postDelayed(freshnessTick, 15_000);
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(catalogTimeout);
        mainHandler.removeCallbacks(freshnessTick);
        CatalogUpdates.shared().unsubscribe(catalogUpdateListener);
        super.onPause();
    }

    private View buildContentView() {
        configureSystemBars();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.royalty_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        applySystemInsets(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = new TextView(this);
        mark.setText("R");
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(getColor(R.color.royalty_on_primary));
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mark.setBackground(roundedDrawable(R.color.royalty_primary, 23, 0));
        header.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        headingParams.setMarginStart(dp(14));

        TextView eyebrow = createText(
                R.string.dashboard_eyebrow, 12, R.color.royalty_primary, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        heading.addView(eyebrow, matchWrap());

        TextView title = createText(R.string.app_name, 30, R.color.royalty_text, Typeface.BOLD);
        heading.addView(title, matchWrap());

        TextView subtitle = createText(
                R.string.dashboard_subtitle, 14, R.color.royalty_text_muted, Typeface.NORMAL);
        heading.addView(subtitle, matchWrap());
        header.addView(heading, headingParams);
        root.addView(header, matchWrap());

        TextView scope = createText(
                R.string.supported_scope, 12, R.color.royalty_text_muted, Typeface.NORMAL);
        scope.setLineSpacing(0, 1.15f);
        root.addView(scope, withTopMargin(matchWrap(), 12));

        root.addView(createSectionLabel(R.string.status_section), withTopMargin(matchWrap(), 20));

        LinearLayout statusCard = createCard(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout statusRows = new LinearLayout(this);
        statusRows.setOrientation(LinearLayout.VERTICAL);
        frameworkStatusDot = createStatusDot();
        frameworkStatusText = createText(0, 14, R.color.royalty_text, Typeface.BOLD);
        statusRows.addView(createConnectionRow(
                frameworkStatusDot, frameworkStatusText), matchWrap());
        telegramStatusDot = createStatusDot();
        telegramStatusText = createText(0, 14, R.color.royalty_text, Typeface.BOLD);
        statusRows.addView(createConnectionRow(
                telegramStatusDot, telegramStatusText), withTopMargin(matchWrap(), 6));
        protectionDetails = createText(0, 12, R.color.royalty_text_muted, Typeface.NORMAL);
        statusRows.addView(protectionDetails, withTopMargin(matchWrap(), 8));
        statusCard.addView(statusRows, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refreshButton = createSecondaryButton(R.string.refresh);
        refreshButton.setOnClickListener(view -> requestCatalog());
        statusCard.addView(refreshButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(statusCard, withTopMargin(matchWrap(), 8));

        updateCard = createCard(LinearLayout.VERTICAL);
        updateCard.setVisibility(View.GONE);
        updateCard.addView(createText(
                R.string.update_available, 16, R.color.royalty_text, Typeface.BOLD), matchWrap());
        updateMessage = createText(0, 13, R.color.royalty_text_muted, Typeface.NORMAL);
        updateCard.addView(updateMessage, withTopMargin(matchWrap(), 3));

        LinearLayout updateActions = new LinearLayout(this);
        updateActions.setOrientation(LinearLayout.HORIZONTAL);
        Button viewReleaseButton = createPrimaryButton(R.string.view_release);
        viewReleaseButton.setOnClickListener(view -> openAvailableRelease());
        updateActions.addView(viewReleaseButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button dismissUpdateButton = createSecondaryButton(R.string.dismiss_update);
        dismissUpdateButton.setOnClickListener(view -> dismissAvailableUpdate());
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        dismissParams.setMarginStart(dp(8));
        updateActions.addView(dismissUpdateButton, dismissParams);
        updateCard.addView(updateActions, withTopMargin(matchWrap(), 10));
        root.addView(updateCard, withTopMargin(matchWrap(), 12));

        LinearLayout notificationCard = createCard(LinearLayout.HORIZONTAL);
        notificationCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout notificationCopy = new LinearLayout(this);
        notificationCopy.setOrientation(LinearLayout.VERTICAL);
        notificationCopy.addView(createText(
                R.string.notifications_title, 16, R.color.royalty_text, Typeface.BOLD), matchWrap());
        notificationCopy.addView(createText(
                R.string.notifications_subtitle, 13, R.color.royalty_text_muted, Typeface.NORMAL),
                withTopMargin(matchWrap(), 3));
        notificationCard.addView(notificationCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        notificationSwitch = new Switch(this);
        notificationSwitch.setContentDescription(getString(R.string.suppress_notifications));
        notificationSwitch.setShowText(false);
        notificationSwitch.setMinimumHeight(dp(48));
        notificationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!renderingSettings) {
                draft.setSuppressNotifications(checked);
                renderCatalogAndHealth();
            }
        });
        notificationCard.addView(notificationSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(notificationCard, withTopMargin(matchWrap(), 12));

        LinearLayout premiumCard = createCard(LinearLayout.HORIZONTAL);
        premiumCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout premiumCopy = new LinearLayout(this);
        premiumCopy.setOrientation(LinearLayout.VERTICAL);
        premiumCopy.addView(createText(
                R.string.premium_title, 16, R.color.royalty_text, Typeface.BOLD), matchWrap());
        premiumCopy.addView(createText(
                R.string.premium_subtitle, 13, R.color.royalty_text_muted, Typeface.NORMAL),
                withTopMargin(matchWrap(), 3));
        premiumCard.addView(premiumCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        premiumSwitch = new Switch(this);
        premiumSwitch.setContentDescription(getString(R.string.local_premium));
        premiumSwitch.setShowText(false);
        premiumSwitch.setMinimumHeight(dp(48));
        premiumSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!renderingSettings) {
                draft.setLocalPremium(checked);
                renderCatalogAndHealth();
            }
        });
        premiumCard.addView(premiumSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(premiumCard, withTopMargin(matchWrap(), 12));

        root.addView(createSectionLabel(R.string.hidden_chats_section), withTopMargin(matchWrap(), 20));
        TextView chatsSubtitle = createText(
                R.string.hidden_chats_subtitle, 13, R.color.royalty_text_muted, Typeface.NORMAL);
        root.addView(chatsSubtitle, withTopMargin(matchWrap(), 3));

        searchInput = new EditText(this);
        searchInput.setHint(R.string.search_chats_hint);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setTextColor(getColor(R.color.royalty_text));
        searchInput.setHintTextColor(getColor(R.color.royalty_text_muted));
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchInput.setPadding(dp(16), 0, dp(16), 0);
        searchInput.setMinimumHeight(dp(48));
        searchInput.setBackground(roundedDrawable(R.color.royalty_surface, 16, 1));

        Button clearSearchButton = createSecondaryButton(R.string.clear_search_symbol);
        clearSearchButton.setContentDescription(getString(R.string.clear_search));
        clearSearchButton.setMinimumWidth(dp(48));
        clearSearchButton.setPadding(0, 0, 0, 0);
        clearSearchButton.setVisibility(View.GONE);
        clearSearchButton.setOnClickListener(view -> searchInput.setText(""));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                applyCatalogFilter(text.toString());
                clearSearchButton.setVisibility(
                        text.length() == 0 ? View.GONE : View.VISIBLE);
            }
        });
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams clearSearchParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        clearSearchParams.setMarginStart(dp(8));
        searchRow.addView(clearSearchButton, clearSearchParams);
        root.addView(searchRow, withTopMargin(matchWrap(), 10));
        selectionControls = new CatalogSelectionControls(this,
                () -> applyCatalogFilter(searchInput.getText().toString()),
                this::selectMatching, () -> {
                    if (draft.undo()) renderCatalogAndHealth();
                });
        root.addView(selectionControls, withTopMargin(matchWrap(), 8));

        dialogList = new ListView(this);
        dialogList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        dialogList.setBackground(roundedDrawable(R.color.royalty_surface, 18, 1));
        dialogList.setDivider(new ColorDrawable(getColor(R.color.royalty_outline)));
        dialogList.setDividerHeight(dp(1));
        dialogList.setClipToOutline(true);
        dialogList.setPadding(0, dp(4), 0, dp(4));
        dialogList.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        dialogList.setNestedScrollingEnabled(true);
        dialogAdapter = createDialogAdapter();
        dialogList.setAdapter(dialogAdapter);
        dialogList.setOnItemClickListener((parent, view, position, id) -> {
            DialogKey key = visibleCatalog.get(position).key();
            boolean selecting = dialogList.isItemChecked(position);
            CatalogEntry entry = visibleCatalog.get(position);
            Long oldOwner = draft.current().boundOwner(key);
            boolean needsReview = draft.current().isHidden(key)
                    && (oldOwner == null || !accountInventory.matches(key.account(), oldOwner));
            if (needsReview) {
                dialogList.setItemChecked(position, true);
                promptRebind(entry);
                return;
            }
            if (!selecting) {
                draft.setHidden(key, false);
                renderCatalogAndHealth();
                return;
            }
            dialogList.setItemChecked(position, draft.current().isHidden(key));
            if (!accountInventory.complete() || entry.ownerId() <= 0
                    || !accountInventory.matches(key.account(), entry.ownerId())) {
                showError("Account ownership is not confirmed. Refresh after opening Telegram.");
                return;
            }
            draft.setHidden(key, true, entry.ownerId());
            renderCatalogAndHealth();
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(360));
        listParams.topMargin = dp(8);
        root.addView(dialogList, listParams);

        saveButton = createPrimaryButton(R.string.save);
        saveButton.setOnClickListener(view -> saveConfiguration());
        root.addView(saveButton, withTopMargin(matchWrap(), 12));
        Button discardButton = createSecondaryButton(R.string.discard);
        discardButton.setOnClickListener(view -> {
            draft.discard();
            renderCached();
        });
        root.addView(discardButton, withTopMargin(matchWrap(), 8));

        TextView hint = createText(
                R.string.refresh_hint, 12, R.color.royalty_text_muted, Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(hint, withTopMargin(matchWrap(), 8));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void promptRebind(CatalogEntry entry) {
        DialogKey key = entry.key();
        boolean canBind = accountInventory.complete() && entry.ownerId() > 0
                && accountInventory.matches(key.account(), entry.ownerId());
        new AlertDialog.Builder(this).setTitle("Review account binding")
                .setMessage(AccountBindingPresentation.reviewPrompt(entry,
                        draft.current(), accountInventory))
                .setNeutralButton("Remove selection", (ignored, which) -> {
                    draft.setHidden(key, false);
                    renderCatalogAndHealth();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(canBind ? "Bind" : "Refresh", (ignored, which) -> {
                    if (!canBind) { requestCatalog(); return; }
                    if (!draft.rebind(key, entry.ownerId(), accountInventory))
                        showError("Account changed. Refresh and review again.");
                    renderCatalogAndHealth();
                }).show();
    }

    private void requestCatalog() {
        catalogRequestTimedOut = false;
        catalogError = "";
        renderCatalogAndHealth();
        mainHandler.removeCallbacks(catalogTimeout);
        try {
            PendingRequestStore.Request request = CatalogRequestClient.request(this);
            requestNonce = request.nonce();
            requestExpiresAt = request.expiresAtElapsedRealtime();
            renderCatalogAndHealth();
            long delay = Math.max(0, requestExpiresAt - SystemClock.elapsedRealtime());
            mainHandler.postDelayed(catalogTimeout, delay + 100);
        } catch (RuntimeException error) {
            requestNonce = null;
            requestExpiresAt = 0;
            catalogRequestTimedOut = true;
            catalogError = "Catalog request failed: " + error.getClass().getSimpleName();
            renderCatalogAndHealth();
        }
    }

    private void maybeCheckForUpdate() {
        SharedPreferences updatePreferences = getSharedPreferences(
                UPDATE_PREFERENCES, Context.MODE_PRIVATE);
        showAvailableUpdate(UpdateRelease.fromStored(
                updatePreferences.getString(CACHED_UPDATE_TAG, ""),
                updatePreferences.getString(CACHED_UPDATE_URL, "")));

        long now = System.currentTimeMillis();
        long lastCheck = updatePreferences.getLong(LAST_UPDATE_CHECK, 0);
        if (lastCheck > now) {
            updatePreferences.edit().putLong(LAST_UPDATE_CHECK, now).apply();
            return;
        }
        if (lastCheck != 0 && now - lastCheck < UPDATE_CHECK_INTERVAL_MILLIS) {
            return;
        }

        updatePreferences.edit().putLong(LAST_UPDATE_CHECK, now).apply();
        updateChecker.check(BuildConfig.VERSION_NAME, this::handleUpdateResult);
    }

    private void handleUpdateResult(UpdateRelease release) {
        if (release == null) {
            return;
        }
        getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHED_UPDATE_TAG, release.tag())
                .putString(CACHED_UPDATE_URL, release.url())
                .apply();
        showAvailableUpdate(release);
    }

    private void showAvailableUpdate(UpdateRelease release) {
        if (release == null || !release.isNewerThan(BuildConfig.VERSION_NAME)) {
            return;
        }
        SharedPreferences updatePreferences = getSharedPreferences(
                UPDATE_PREFERENCES, Context.MODE_PRIVATE);
        if (release.tag().equals(updatePreferences.getString(DISMISSED_UPDATE_TAG, ""))) {
            return;
        }

        availableUpdate = release;
        updateMessage.setText(getString(
                R.string.update_available_message,
                release.version(),
                BuildConfig.VERSION_NAME));
        updateCard.setVisibility(View.VISIBLE);
    }

    private void openAvailableRelease() {
        if (availableUpdate == null) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(availableUpdate.url())));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.release_page_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void dismissAvailableUpdate() {
        if (availableUpdate == null) {
            return;
        }
        getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(DISMISSED_UPDATE_TAG, availableUpdate.tag())
                .apply();
        availableUpdate = null;
        updateCard.setVisibility(View.GONE);
    }

    private void renderCached() {
        renderSettings();
        renderCatalogAndHealth();
    }

    private void renderSettings() {
        preferencesAvailable = preferences != null;
        if (preferencesAvailable) {
            try {
                draft.loadSaved(ConfigStore.load(preferences));
            } catch (RuntimeException error) {
                preferencesAvailable = false;
            }
        }

        renderingSettings = true;
        setSuppressNotifications(draft.current().suppressNotifications());
        setLocalPremium(draft.current().localPremium());
        renderingSettings = false;
        boolean editable = draft.initialized() && preferencesAvailable;
        notificationSwitch.setEnabled(editable);
        premiumSwitch.setEnabled(editable);
        dialogList.setEnabled(editable);
        saveButton.setEnabled(editable);
    }

    private void renderCatalogAndHealth() {
        catalog.clear();
        catalog.addAll(catalogRepository.loadCatalog());
        addMissingSelections(draft.current().hiddenDialogs());
        java.util.Collections.sort(catalog);
        accountInventory = catalogRepository.loadInventory();
        selectionControls.update(accountInventory, catalog, CatalogSelection.selectedCount(draft.current()),
                draft.undoSize(), draft.initialized() && preferencesAvailable);
        applyCatalogFilter(searchInput.getText().toString());
        updateConnectionStatus(catalogRepository.loadHookStatuses());
    }

    private void addMissingSelections(Set<DialogKey> selected) {
        Set<DialogKey> present = new HashSet<>();
        for (CatalogEntry entry : catalog) {
            present.add(entry.key());
        }
        for (DialogKey key : selected) {
            if (!present.contains(key)) {
                catalog.add(new CatalogEntry(key, "Unavailable from current catalog"));
            }
        }
    }

    private void saveConfiguration() {
        if (!preferencesAvailable || preferences == null || !draft.canSave()) {
            showError(getString(R.string.framework_inactive));
            return;
        }
        try {
            HiddenConfig current = draft.current();
            if (!draft.canSaveAgainst(accountInventory)) {
                showError("Account ownership changed or is incomplete. Refresh and review selections before saving.");
                return;
            }
            boolean saved = ConfigStore.save(preferences, current);
            draft.markSaved(saved);
            if (!saved) {
                showError(getString(R.string.save_failed));
                return;
            }
            renderCatalogAndHealth();
            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            draft.markSaved(false);
            preferencesAvailable = false;
            saveButton.setEnabled(false);
            showError(getString(R.string.framework_inactive));
        }
    }

    private void setSuppressNotifications(boolean enabled) {
        notificationSwitch.setChecked(enabled);
    }

    private void setLocalPremium(boolean enabled) {
        premiumSwitch.setChecked(enabled);
    }

    private String formatEntry(CatalogEntry entry) {
        return AccountBindingPresentation.row(entry, draft.current(), accountInventory);
    }

    private void selectMatching() {
        List<CatalogEntry> eligible = new ArrayList<>();
        for (CatalogEntry entry : visibleCatalog) {
            if (accountInventory.complete() && entry.ownerId() > 0
                    && accountInventory.matches(entry.key().account(), entry.ownerId())
                    && !draft.current().isHidden(entry.key())) eligible.add(entry);
        }
        CatalogSelection.selectMatching(draft, eligible, true);
        renderCatalogAndHealth();
    }

    private void applyCatalogFilter(String rawQuery) {
        visibleCatalog.clear();
        visibleCatalog.addAll(CatalogSelection.filter(catalog, draft.current(), rawQuery,
                selectionControls.accountFilter(), selectionControls.hiddenOnly()));
        dialogAdapter.clear();
        for (CatalogEntry entry : visibleCatalog) dialogAdapter.add(formatEntry(entry));
        dialogAdapter.notifyDataSetChanged();
        dialogList.clearChoices();
        for (int index = 0; index < visibleCatalog.size(); index++)
            dialogList.setItemChecked(index, draft.current().isHidden(visibleCatalog.get(index).key()));
        selectionControls.update(accountInventory, catalog, CatalogSelection.selectedCount(draft.current()),
                draft.undoSize(), draft.initialized() && preferencesAvailable);
    }

    private void updateConnectionStatus(Map<String, String> statuses) {
        ProtectionStatus protection = ProtectionStatus.evaluate(statuses,
                catalogRepository.loadHookDetails(), catalogRepository.observedAtMillis(),
                System.currentTimeMillis(), catalogRequestTimedOut);
        protectionDetails.setText(DiagnosticsFormatter.describe(statuses,
                catalogRepository.loadHookDetails(), catalogRepository.observedAtMillis(),
                System.currentTimeMillis(), requestNonce != null, catalogError,
                installedTelegramVersion()));
        setConnectionStatus(
                frameworkStatusDot,
                frameworkStatusText,
                R.string.framework_connection,
                preferencesAvailable);
        setConnectionStatus(
                telegramStatusDot,
                telegramStatusText,
                protection.unsupported() ? R.string.telegram_unsupported : R.string.telegram_connection,
                protection.working());
    }

    @SuppressWarnings("deprecation")
    private String installedTelegramVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo("org.telegram.messenger", 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return (info.versionName == null ? "unknown" : info.versionName) + " (" + code + ")";
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return "not found (" + error.getClass().getSimpleName() + ")";
        }
    }

    private void setConnectionStatus(
            TextView dot, TextView label, int labelResource, boolean working) {
        dot.setTextColor(getColor(
                working ? R.color.royalty_success : R.color.royalty_error));
        label.setText(getString(labelResource)
                + " · "
                + getString(working ? R.string.connection_working : R.string.connection_not_working));
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void applySystemInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    dp(20) + insets.getSystemWindowInsetLeft(),
                    dp(16) + insets.getSystemWindowInsetTop(),
                    dp(20) + insets.getSystemWindowInsetRight(),
                    dp(16) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.royalty_background));
        getWindow().setNavigationBarColor(getColor(R.color.royalty_background));
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        boolean darkIcons = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        if (darkIcons) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private TextView createText(int stringResource, int sizeSp, int colorResource, int style) {
        TextView view = new TextView(this);
        if (stringResource != 0) {
            view.setText(stringResource);
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(getColor(colorResource));
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private TextView createSectionLabel(int stringResource) {
        TextView label = createText(stringResource, 13, R.color.royalty_text, Typeface.BOLD);
        label.setLetterSpacing(0.04f);
        return label;
    }

    private TextView createStatusDot() {
        TextView dot = createText(0, 18, R.color.royalty_error, Typeface.BOLD);
        dot.setText("●");
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return dot;
    }

    private LinearLayout createConnectionRow(TextView dot, TextView label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(dot, new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private LinearLayout createCard(int orientation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(orientation);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedDrawable(R.color.royalty_surface, 18, 1));
        card.setElevation(dp(2));
        return card;
    }

    private Button createPrimaryButton(int stringResource) {
        Button button = createButton(stringResource);
        button.setTextColor(getColor(R.color.royalty_on_primary));
        button.setBackground(rippleBackground(R.color.royalty_primary, 16, 0));
        return button;
    }

    private Button createSecondaryButton(int stringResource) {
        Button button = createButton(stringResource);
        button.setTextColor(getColor(R.color.royalty_primary));
        button.setBackground(rippleBackground(R.color.royalty_surface_variant, 14, 0));
        return button;
    }

    private Button createButton(int stringResource) {
        Button button = new Button(this);
        button.setText(stringResource);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(20), 0, dp(20), 0);
        button.setStateListAnimator(null);
        return button;
    }

    private ArrayAdapter<String> createDialogAdapter() {
        ColorStateList checkColors = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {getColor(R.color.royalty_primary), getColor(R.color.royalty_text_muted)});
        return new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_multiple_choice, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                row.setTextColor(getColor(R.color.royalty_text));
                row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(dp(64));
                row.setPadding(dp(16), dp(8), dp(12), dp(8));
                row.setCheckMarkTintList(checkColors);
                row.setBackgroundColor(Color.TRANSPARENT);
                return row;
            }
        };
    }

    private Drawable rippleBackground(int colorResource, int radiusDp, int strokeDp) {
        return new RippleDrawable(
                ColorStateList.valueOf(getColor(R.color.royalty_ripple)),
                roundedDrawable(colorResource, radiusDp, strokeDp),
                null);
    }

    private GradientDrawable roundedDrawable(int colorResource, int radiusDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(getColor(colorResource));
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), getColor(R.color.royalty_outline));
        }
        return drawable;
    }

    private LinearLayout.LayoutParams withTopMargin(
            LinearLayout.LayoutParams params, int marginDp) {
        params.topMargin = dp(marginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
