package io.github.cepeter.royalty.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import io.github.cepeter.royalty.catalog.CatalogProtocol;
import java.util.Map;

public final class CatalogRequestBridge {
    private CatalogRequestBridge() {}

    public static BroadcastReceiver register(Context context, CatalogSnapshotStore store) {
        Context applicationContext = context.getApplicationContext();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent request) {
                handleRequest(applicationContext, store, request);
            }
        };
        IntentFilter filter = new IntentFilter(CatalogProtocol.ACTION_REQUEST);
        if (Build.VERSION.SDK_INT >= 33) {
            applicationContext.registerReceiver(
                    receiver,
                    filter,
                    CatalogProtocol.REQUEST_PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED);
        } else {
            registerLegacy(applicationContext, receiver, filter);
        }
        return receiver;
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerLegacy(
            Context context, BroadcastReceiver receiver, IntentFilter filter) {
        context.registerReceiver(
                receiver, filter, CatalogProtocol.REQUEST_PERMISSION, null);
    }

    private static void handleRequest(
            Context context, CatalogSnapshotStore store, Intent request) {
        PendingIntent callback = getCallback(request);
        if (callback == null) {
            ModernHookBridge.log("TelegramChatHider: rejected malformed catalog request");
            return;
        }

        CatalogSnapshotStore.Snapshot snapshot = store.snapshot();
        try {
            for (Map.Entry<Integer, CatalogSnapshotStore.AccountSnapshot> entry
                    : snapshot.accounts().entrySet()) {
                CatalogSnapshotStore.AccountSnapshot account = entry.getValue();
                Intent result = result(CatalogProtocol.TYPE_ACCOUNT)
                        .putExtra(CatalogProtocol.EXTRA_ACCOUNT, entry.getKey())
                        .putExtra(CatalogProtocol.EXTRA_IDS, account.ids())
                        .putExtra(CatalogProtocol.EXTRA_TITLES, account.titles());
                callback.send(context, Activity.RESULT_OK, result);
            }
            sendStatuses(context, callback, snapshot);
            callback.send(
                    context,
                    Activity.RESULT_OK,
                    result(CatalogProtocol.TYPE_COMPLETE));
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            ModernHookBridge.log("TelegramChatHider: catalog response failed: "
                    + error.getClass().getSimpleName());
        }
    }

    private static void sendStatuses(
            Context context,
            PendingIntent callback,
            CatalogSnapshotStore.Snapshot snapshot)
            throws PendingIntent.CanceledException {
        int count = snapshot.statuses().size();
        String[] hooks = new String[count];
        String[] values = new String[count];
        String[] details = new String[count];
        int index = 0;
        for (Map.Entry<String, CatalogSnapshotStore.StatusSnapshot> entry
                : snapshot.statuses().entrySet()) {
            hooks[index] = entry.getKey();
            values[index] = entry.getValue().status();
            details[index] = entry.getValue().detail();
            index++;
        }
        Intent status = result(CatalogProtocol.TYPE_STATUS)
                .putExtra(CatalogProtocol.EXTRA_STATUS_HOOKS, hooks)
                .putExtra(CatalogProtocol.EXTRA_STATUS_VALUES, values)
                .putExtra(CatalogProtocol.EXTRA_STATUS_DETAILS, details);
        callback.send(context, Activity.RESULT_OK, status);
    }

    private static Intent result(String type) {
        return new Intent(CatalogProtocol.ACTION_RESULT)
                .putExtra(CatalogProtocol.EXTRA_TYPE, type);
    }

    private static PendingIntent getCallback(Intent request) {
        if (request == null || !CatalogProtocol.ACTION_REQUEST.equals(request.getAction())) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return request.getParcelableExtra(
                    CatalogProtocol.EXTRA_CALLBACK, PendingIntent.class);
        }
        return getLegacyCallback(request);
    }

    @SuppressWarnings("deprecation")
    private static PendingIntent getLegacyCallback(Intent request) {
        return request.getParcelableExtra(CatalogProtocol.EXTRA_CALLBACK);
    }
}
