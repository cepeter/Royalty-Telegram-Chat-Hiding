package io.github.cepeter.royalty.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Identity-balanced visibility with bounded, generation-guarded recreation grace. */
public final class ActivityVisibility {
    public static final long RECREATION_GRACE_MS = 700;
    private final Set<Object> started = Collections.newSetFromMap(new IdentityHashMap<>());
    private long generation;
    private boolean pending;
    public synchronized void started(Object activity) {
        if (started.add(activity)) { generation++; pending = false; }
    }
    public synchronized boolean stopped(Object activity, boolean changingConfiguration) {
        if (!started.remove(activity)) return false;
        generation++;
        pending = started.isEmpty() && changingConfiguration;
        return started.isEmpty() && !changingConfiguration;
    }
    public synchronized long pendingGeneration() { return pending ? generation : -1; }
    public synchronized boolean reconcile(long expectedGeneration) {
        if (!pending || expectedGeneration != generation || !started.isEmpty()) return false;
        pending = false;
        return true;
    }
}
