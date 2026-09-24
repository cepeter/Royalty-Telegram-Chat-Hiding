package io.github.cepeter.royalty.catalog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import io.github.cepeter.royalty.core.CatalogSubmission;

public final class CatalogResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !CatalogProtocol.ACTION_RESULT.equals(intent.getAction())) {
            return;
        }
        String nonce = intent.getStringExtra(CatalogProtocol.EXTRA_NONCE);
        PendingRequestStore pending = new PendingRequestStore(context);
        if (!pending.isActive(nonce, SystemClock.elapsedRealtime())) {
            return;
        }

        String type = intent.getStringExtra(CatalogProtocol.EXTRA_TYPE);
        CatalogRepository repository = new CatalogRepository(context);
        try {
            if (CatalogProtocol.TYPE_ACCOUNT.equals(type)) {
                receiveAccount(intent, repository);
            } else if (CatalogProtocol.TYPE_STATUS.equals(type)) {
                receiveStatuses(intent, repository);
            } else if (CatalogProtocol.TYPE_COMPLETE.equals(type)) {
                pending.complete(nonce);
                context.sendBroadcast(new Intent(CatalogProtocol.ACTION_UPDATED)
                        .setPackage(CatalogProtocol.MODULE_PACKAGE));
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed callback frames must not replace stored catalog data.
        }
    }

    private static void receiveAccount(Intent intent, CatalogRepository repository) {
        int account = intent.getIntExtra(CatalogProtocol.EXTRA_ACCOUNT, -1);
        long[] ids = intent.getLongArrayExtra(CatalogProtocol.EXTRA_IDS);
        String[] titles = intent.getStringArrayExtra(CatalogProtocol.EXTRA_TITLES);
        CatalogSubmission.sanitize(account, ids, titles);
        repository.replaceAccount(account, ids, titles);
    }

    private static void receiveStatuses(Intent intent, CatalogRepository repository) {
        String[] hooks = intent.getStringArrayExtra(CatalogProtocol.EXTRA_STATUS_HOOKS);
        String[] statuses = intent.getStringArrayExtra(CatalogProtocol.EXTRA_STATUS_VALUES);
        String[] details = intent.getStringArrayExtra(CatalogProtocol.EXTRA_STATUS_DETAILS);
        if (hooks == null || statuses == null || details == null
                || hooks.length != statuses.length || hooks.length != details.length
                || hooks.length > CatalogProtocol.MAX_STATUS_COUNT) {
            throw new IllegalArgumentException("invalid status arrays");
        }
        String[] safeDetails = new String[details.length];
        for (int index = 0; index < hooks.length; index++) {
            validateToken(hooks[index], "hook");
            validateToken(statuses[index], "status");
            String detail = details[index] == null ? "" : details[index];
            safeDetails[index] = detail.length() > CatalogProtocol.MAX_STATUS_DETAIL_LENGTH
                    ? detail.substring(0, CatalogProtocol.MAX_STATUS_DETAIL_LENGTH)
                    : detail;
        }
        for (int index = 0; index < hooks.length; index++) {
            repository.recordStatus(hooks[index], statuses[index], safeDetails[index]);
        }
    }

    static String validateToken(String value, String name) {
        if (value == null || value.isEmpty()
                || value.length() > CatalogProtocol.MAX_STATUS_NAME_LENGTH) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'a' && character <= 'z') || character == '_')) {
                throw new IllegalArgumentException(name + " contains invalid characters");
            }
        }
        return value;
    }
}
