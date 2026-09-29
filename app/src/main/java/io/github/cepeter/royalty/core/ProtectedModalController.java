package io.github.cepeter.royalty.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Revokes modal actions and closes their separate windows whenever settings relock. */
public final class ProtectedModalController {
    private final SettingsAccess access;
    private final Map<Object, Runnable> open = new IdentityHashMap<>();

    public ProtectedModalController(SettingsAccess access) { this.access = access; }

    public boolean open(Object token, Runnable dismiss) {
        boolean allowed;
        synchronized (this) {
            allowed = access.allowed();
            if (allowed) open.put(token, dismiss);
        }
        if (!allowed) dismiss.run();
        return allowed;
    }

    public synchronized boolean consume(Object token) {
        if (!access.allowed()) return false;
        return open.remove(token) != null;
    }

    public synchronized void closed(Object token) { open.remove(token); }

    public void closeAll() {
        ArrayList<Runnable> dismiss;
        synchronized (this) {
            dismiss = new ArrayList<>(open.values());
            open.clear();
        }
        for (Runnable action : dismiss) action.run();
    }
}
