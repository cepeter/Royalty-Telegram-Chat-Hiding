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

    /** Loads persisted nonce expiries after checking for a changed boot marker. */
    public PendingRequestStore(Context context) {
        this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE), readBootId(context));
    }

    /**
     * Loads persisted nonce expiries after discarding entries from a different or unknown boot.
     *
     * @param preferences storage for nonce expiries and the boot marker
     * @param bootId current boot count, or a negative value when it cannot be verified
     */
    PendingRequestStore(SharedPreferences preferences, int bootId) {
        this.preferences = preferences;
        requests = new HashMap<>();
        boolean persistedRequestsAreCurrent = dropNoncesFromPreviousBoot(bootId);
        if (!persistedRequestsAreCurrent) {
            return;
        }
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof Long) {
                requests.put(entry.getKey(), (Long) entry.getValue());
            }
        }
    }

    /** Returns the system boot count, or {@code -1} if it is missing or cannot be read. */
    private static int readBootId(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException error) {
            return -1;
        }
    }

    /**
     * Expiries are {@code elapsedRealtime} based and restart at zero on boot, so
     * persisted nonces from a previous or unverifiable boot must not appear active again.
     *
     * @return whether persisted nonce entries are safe to load for the current boot
     */
    private boolean dropNoncesFromPreviousBoot(int bootId) {
        if (bootId >= 0 && preferences.getInt(BOOT_ID, -1) == bootId) {
            return true;
        }
        SharedPreferences.Editor editor = preferences.edit().clear();
        if (bootId >= 0) {
            editor.putInt(BOOT_ID, bootId);
        }
        boolean cleared = editor.commit();
        return bootId >= 0 && cleared;
    }

    /** Copies nonce expiries into an in-memory store without persistence. */
    PendingRequestStore(Map<String, Long> requests) {
        this.requests = new HashMap<>(requests);
        this.preferences = null;
    }

    /**
     * Replaces pending requests with a fresh nonce, preserving any stored boot marker.
     *
     * @param nowElapsedRealtime current monotonic time in milliseconds since boot
     * @return the nonce and its expiration time on the same clock
     * @throws IllegalStateException if the new request cannot be persisted
     */
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
