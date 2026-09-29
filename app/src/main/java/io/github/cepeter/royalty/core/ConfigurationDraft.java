package io.github.cepeter.royalty.core;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/** An editable configuration whose saved baseline is independent of catalog refreshes. */
public final class ConfigurationDraft {
    private HiddenConfig baseline = HiddenConfig.empty();
    private HiddenConfig current = HiddenConfig.empty();
    private boolean requireVerifiedBaseline;
    private boolean initialized;

    public boolean initialized() { return initialized; }
    public boolean dirty() { return initialized && !baseline.equals(current); }
    public boolean canSave() { return initialized; }
    public HiddenConfig baseline() { return baseline; }
    public HiddenConfig current() { return current; }

    public void loadSaved(HiddenConfig saved) {
        if (saved == null) return;
        // Once a commit fails, preference reads may contain any failed attempt. Only a
        // verified save can advance the baseline, even after recreation or late callbacks.
        if (requireVerifiedBaseline) return;
        boolean preserveEdit = dirty();
        baseline = saved;
        if (!preserveEdit) current = saved;
        initialized = true;
    }

    public boolean setHidden(DialogKey key, boolean hidden) {
        if (!initialized || key == null) return false;
        Set<String> keys = encoded(current);
        if (hidden) keys.add(key.toString()); else keys.remove(key.toString());
        current = HiddenConfig.fromStrings(keys, current.suppressNotifications(), current.localPremium());
        return true;
    }

    public boolean setSuppressNotifications(boolean value) {
        if (!initialized) return false;
        current = HiddenConfig.fromStrings(encoded(current), value, current.localPremium());
        return true;
    }

    public boolean setLocalPremium(boolean value) {
        if (!initialized) return false;
        current = HiddenConfig.fromStrings(encoded(current), current.suppressNotifications(), value);
        return true;
    }

    public boolean markSaved(boolean persisted) {
        if (!initialized) return false;
        if (!persisted) {
            requireVerifiedBaseline = true;
            return false;
        }
        baseline = current;
        return true;
    }

    public void discard() {
        if (initialized) current = baseline;
    }

    public State snapshot() {
        return new State(initialized, encoded(baseline), baseline.suppressNotifications(),
                baseline.localPremium(), encoded(current), current.suppressNotifications(),
                current.localPremium(), requireVerifiedBaseline);
    }

    public static ConfigurationDraft restore(State state) {
        ConfigurationDraft draft = new ConfigurationDraft();
        if (state != null && state.initialized) {
            draft.baseline = HiddenConfig.fromStrings(state.baselineKeys,
                    state.baselineNotifications, state.baselinePremium);
            draft.current = HiddenConfig.fromStrings(state.currentKeys,
                    state.currentNotifications, state.currentPremium);
            draft.requireVerifiedBaseline = state.requireVerifiedBaseline;
            draft.initialized = true;
        }
        return draft;
    }

    private static Set<String> encoded(HiddenConfig config) {
        Set<String> keys = new HashSet<>();
        for (DialogKey key : config.hiddenDialogs()) keys.add(key.toString());
        return keys;
    }

    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        private final boolean initialized;
        private final Set<String> baselineKeys;
        private final boolean baselineNotifications;
        private final boolean baselinePremium;
        private final Set<String> currentKeys;
        private final boolean currentNotifications;
        private final boolean currentPremium;
        private final boolean requireVerifiedBaseline;

        private State(boolean initialized, Set<String> baselineKeys, boolean baselineNotifications,
                boolean baselinePremium, Set<String> currentKeys, boolean currentNotifications,
                boolean currentPremium, boolean requireVerifiedBaseline) {
            this.initialized = initialized;
            this.baselineKeys = new HashSet<>(baselineKeys);
            this.baselineNotifications = baselineNotifications;
            this.baselinePremium = baselinePremium;
            this.currentKeys = new HashSet<>(currentKeys);
            this.currentNotifications = currentNotifications;
            this.currentPremium = currentPremium;
            this.requireVerifiedBaseline = requireVerifiedBaseline;
        }
    }
}
