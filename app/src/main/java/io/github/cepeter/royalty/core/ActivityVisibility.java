package io.github.cepeter.royalty.core;

/** Balances activity starts/stops while ignoring rotation as a genuine background. */
public final class ActivityVisibility {
    private int started;
    private int recreating;
    public synchronized void started() {
        started++;
        if (recreating > 0) recreating--;
    }
    public synchronized boolean stopped(boolean changingConfiguration) {
        if (started > 0) started--;
        if (changingConfiguration) recreating++;
        return started == 0 && !changingConfiguration && recreating == 0;
    }
}
