package io.github.cepeter.royalty.catalog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SuppressLint("ApplySharedPref")
public final class PendingRequestStore {
    private static final String PREFERENCES_NAME = "catalog_requests";
    private static final String BOOT_ID = "boot_id";

    private final Map<String, Long> requests;
    private final SharedPreferences preferences;
    private final SecureRandom random = new SecureRandom();

    public PendingRequestStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        requests = new HashMap<>();
        dropNoncesFromPreviousBoot(context);
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof Long) {
                requests.put(entry.getKey(), (Long) entry.getValue());
            }
        }
    }

    /**
     * Expiries are {@code elapsedRealtime} based and restart at zero on boot, so
     * persisted nonces from a previous boot must not appear active again.
     */
    private void dropNoncesFromPreviousBoot(Context context) {
        int bootId;
        try {
            bootId = Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException error) {
            return;
        }
        if (bootId < 0 || preferences.getInt(BOOT_ID, bootId) == bootId) return;
        preferences.edit().clear().putInt(BOOT_ID, bootId).commit();
    }

    PendingRequestStore(Map<String, Long> requests) {
        this.requests = new HashMap<>(requests);
        this.preferences = null;
    }

    public synchronized Request create(long nowElapsedRealtime) {
        requests.clear();
        String nonce;
        do {
            nonce = nextNonce();
        } while (requests.containsKey(nonce));
        long expiry = nowElapsedRealtime + CatalogProtocol.NONCE_LIFETIME_MS;
        if (preferences != null) {
            int bootId = preferences.getInt(BOOT_ID, Integer.MIN_VALUE);
            SharedPreferences.Editor editor = preferences.edit().clear();
            if (bootId != Integer.MIN_VALUE) {
                editor.putInt(BOOT_ID, bootId);
            }
            editor.putLong(nonce, expiry);
            if (!editor.commit()) {
                throw new IllegalStateException("catalog request could not be persisted");
            }
        }
        requests.put(nonce, expiry);
        return new Request(nonce, expiry);
    }

    public synchronized boolean isActive(String nonce, long nowElapsedRealtime) {
        removeExpired(nowElapsedRealtime);
        Long expiry = requests.get(nonce);
        return expiry != null && nowElapsedRealtime < expiry;
    }

    public synchronized boolean complete(String nonce) {
        if (nonce == null) {
            return false;
        }
        if (preferences != null) {
            if (!preferences.edit().remove(nonce).commit()) return false;
        }
        return requests.remove(nonce) != null;
    }

    private void removeExpired(long nowElapsedRealtime) {
        SharedPreferences.Editor editor = preferences == null ? null : preferences.edit();
        boolean changed = false;
        Iterator<Map.Entry<String, Long>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (nowElapsedRealtime >= entry.getValue()) {
                iterator.remove();
                if (editor != null) {
                    editor.remove(entry.getKey());
                    changed = true;
                }
            }
        }
        if (changed) {
            editor.commit();
        }
    }

    private String nextNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder value = new StringBuilder(32);
        for (byte item : bytes) {
            value.append(Character.forDigit((item >>> 4) & 0x0f, 16));
            value.append(Character.forDigit(item & 0x0f, 16));
        }
        return value.toString();
    }

    public static final class Request {
        private final String nonce;
        private final long expiresAtElapsedRealtime;

        private Request(String nonce, long expiresAtElapsedRealtime) {
            this.nonce = nonce;
            this.expiresAtElapsedRealtime = expiresAtElapsedRealtime;
        }

        public String nonce() {
            return nonce;
        }

        public long expiresAtElapsedRealtime() {
            return expiresAtElapsedRealtime;
        }
    }
}
