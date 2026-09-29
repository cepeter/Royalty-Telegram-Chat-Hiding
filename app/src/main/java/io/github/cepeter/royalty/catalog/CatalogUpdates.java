package io.github.cepeter.royalty.catalog;

import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local refresh completion bus and active-request gate. */
public final class CatalogUpdates {
    public interface Listener { void onCompleted(Event event); }
    public static final class Event {
        private final String nonce;
        private final boolean success;
        private final String detail;
        private Event(String nonce, boolean success, String detail) {
            this.nonce = nonce; this.success = success; this.detail = detail;
        }
        public String nonce() { return nonce; }
        public boolean success() { return success; }
        public String detail() { return detail; }
    }

    private static final CatalogUpdates SHARED = new CatalogUpdates();
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private String activeNonce;
    private long expiresAtElapsedRealtime;

    public static CatalogUpdates shared() { return SHARED; }
    public void subscribe(Listener listener) { listeners.add(listener); }
    public void unsubscribe(Listener listener) { listeners.remove(listener); }
    public synchronized void begin(String nonce, long expiresAt) {
        activeNonce = nonce;
        expiresAtElapsedRealtime = expiresAt;
    }
    public synchronized String activeNonce() { return activeNonce; }
    public synchronized long expiresAtElapsedRealtime() { return expiresAtElapsedRealtime; }
    public boolean complete(String nonce, boolean success) {
        return complete(nonce, success, success ? "" : "Catalog response failed");
    }
    public boolean complete(String nonce, boolean success, String detail) {
        synchronized (this) {
            if (nonce == null || !nonce.equals(activeNonce)) return false;
            activeNonce = null;
            expiresAtElapsedRealtime = 0;
        }
        Event event = new Event(nonce, success, detail == null ? "" : detail);
        for (Listener listener : listeners) listener.onCompleted(event);
        return true;
    }
}
