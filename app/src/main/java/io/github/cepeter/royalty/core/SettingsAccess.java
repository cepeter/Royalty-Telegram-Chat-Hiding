package io.github.cepeter.royalty.core;

/** Access follows confirmed saved policy, never an unsaved draft. */
public final class SettingsAccess {
    private boolean known;
    private boolean authenticationRequired;
    private boolean unlocked;
    public synchronized void learn(HiddenConfig saved) {
        if (saved == null) return;
        if (saved.authenticate() && (!known || !authenticationRequired)) unlocked = false;
        known = true;
        authenticationRequired = saved.authenticate();
        if (!authenticationRequired) unlocked = true;
    }
    public synchronized boolean known() { return known; }
    public synchronized boolean authenticationRequired() { return authenticationRequired; }
    public synchronized boolean allowed() { return known && (!authenticationRequired || unlocked); }
    public synchronized void authenticated() { if (known && authenticationRequired) unlocked = true; }
    public synchronized void background() { unlocked = false; }
}
