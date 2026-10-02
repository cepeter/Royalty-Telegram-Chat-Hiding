package io.github.cepeter.royalty.catalog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import java.util.Optional;

/** Explicit, non-exported callback endpoint for the currently outstanding catalog request. */
public final class CatalogResultReceiver extends BroadcastReceiver {
    private static CatalogBatch batch;

    static synchronized void begin(String nonce) {
        batch = new CatalogBatch(nonce);
    }

    /**
     * Drops a staged sequence whose request expired before completion.
     *
     * <p>A timed-out request never receives another frame: the nonce guard above rejects late
     * frames, and {@code fail} bails once the active nonce is already cleared. This is the only
     * path that releases the retained catalog snapshot.
     */
    public static synchronized void clear(String nonce) {
        if (nonce != null && batch != null && batch.owns(nonce)) batch = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !CatalogProtocol.ACTION_RESULT.equals(intent.getAction())) return;
        String nonce;
        try {
            nonce = intent.getStringExtra(CatalogProtocol.EXTRA_NONCE);
        } catch (RuntimeException error) {
            return;
        }
        PendingRequestStore pending = new PendingRequestStore(context);
        if (!pending.isActive(nonce, SystemClock.elapsedRealtime())) return;
        synchronized (CatalogResultReceiver.class) {
            if (batch == null) return;
            try {
                String type = intent.getStringExtra(CatalogProtocol.EXTRA_TYPE);
                if (CatalogProtocol.TYPE_BEGIN.equals(type)) {
                    if (!batch.begin(nonce, intent.getIntArrayExtra(CatalogProtocol.EXTRA_EXPECTED_ACCOUNTS),
                            intent.getLongArrayExtra(CatalogProtocol.EXTRA_OWNER_IDS),
                            intent.getStringArrayExtra(CatalogProtocol.EXTRA_OWNER_LABELS),
                            intent.getBooleanExtra(CatalogProtocol.EXTRA_INVENTORY_COMPLETE, false),
                            intent.getStringExtra(CatalogProtocol.EXTRA_PROCESS_SESSION))) fail(pending, nonce, "Invalid catalog frame: " + type);
                } else if (CatalogProtocol.TYPE_ACCOUNT.equals(type)) {
                    if (!batch.account(nonce, intent.getIntExtra(CatalogProtocol.EXTRA_ACCOUNT, -1),
                            intent.getLongExtra(CatalogProtocol.EXTRA_OWNER_ID, 0),
                            intent.getLongArrayExtra(CatalogProtocol.EXTRA_IDS),
                            intent.getStringArrayExtra(CatalogProtocol.EXTRA_TITLES))) fail(pending, nonce, "Invalid catalog frame: " + type);
                } else if (CatalogProtocol.TYPE_STATUS.equals(type)) {
                    if (!batch.status(nonce,
                            intent.getStringArrayExtra(CatalogProtocol.EXTRA_STATUS_HOOKS),
                            intent.getStringArrayExtra(CatalogProtocol.EXTRA_STATUS_VALUES),
                            intent.getStringArrayExtra(CatalogProtocol.EXTRA_STATUS_DETAILS),
                            intent.getStringExtra(CatalogProtocol.EXTRA_PROCESS_SESSION),
                            intent.getLongExtra(CatalogProtocol.EXTRA_OBSERVED_AT, 0))) fail(pending, nonce, "Invalid catalog frame: " + type);
                } else if (CatalogProtocol.TYPE_COMPLETE.equals(type)) {
                    Optional<CatalogBatch.Snapshot> snapshot = batch.complete(nonce);
                    boolean saved = snapshot.isPresent()
                            && new CatalogRepository(context).replaceSnapshot(snapshot.get());
                    boolean consumed = pending.complete(nonce);
                    CatalogUpdates.shared().complete(nonce, saved && consumed,
                            saved && consumed ? "" : "Incomplete catalog response or storage failure");
                    batch = null;
                } else {
                    fail(pending, nonce, "Invalid catalog frame: " + type);
                }
            } catch (RuntimeException error) {
                fail(pending, nonce, "Catalog callback error: " + error.getClass().getSimpleName());
            }
        }
    }

    private static void fail(PendingRequestStore pending, String nonce, String reason) {
        if (batch == null || !nonce.equals(CatalogUpdates.shared().activeNonce())) return;
        pending.complete(nonce);
        CatalogUpdates.shared().complete(nonce, false, reason);
        batch = null;
    }
}
