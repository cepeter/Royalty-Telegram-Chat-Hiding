package io.github.cepeter.royalty.catalog;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

public final class CatalogRequestClient {
    private CatalogRequestClient() {}

    public static long request(Context context) {
        Context applicationContext = context.getApplicationContext();
        PendingRequestStore.Request pending = new PendingRequestStore(applicationContext)
                .create(SystemClock.elapsedRealtime());
        Intent callbackIntent = new Intent(CatalogProtocol.ACTION_RESULT)
                .setComponent(new ComponentName(applicationContext, CatalogResultReceiver.class))
                .setData(Uri.parse("catalog-callback:" + pending.nonce()))
                .putExtra(CatalogProtocol.EXTRA_NONCE, pending.nonce());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent callback = PendingIntent.getBroadcast(
                applicationContext, 0, callbackIntent, flags);
        Intent request = new Intent(CatalogProtocol.ACTION_REQUEST)
                .setPackage(CatalogProtocol.TELEGRAM_PACKAGE)
                .putExtra(CatalogProtocol.EXTRA_CALLBACK, callback);
        applicationContext.sendBroadcast(request);
        return pending.expiresAtElapsedRealtime();
    }
}
