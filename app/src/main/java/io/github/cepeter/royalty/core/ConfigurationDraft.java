package io.github.cepeter.royalty.core;

import java.io.Serializable;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Set;

/** An editable configuration whose saved baseline is independent of catalog refreshes. */
public final class ConfigurationDraft {
    private HiddenConfig baseline = HiddenConfig.empty();
    private HiddenConfig current = HiddenConfig.empty();
    private boolean requireVerifiedBaseline;
    private boolean initialized;
    private final ArrayDeque<HiddenConfig> undo = new ArrayDeque<>();

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
        remember();
        current = current.withSelection(key, hidden, null);
        return true;
    }

    public boolean setSuppressNotifications(boolean value) {
        if (!initialized) return false;
        remember();
        current = current.withOptions(value, current.localPremium());
        return true;
    }

    public boolean setLocalPremium(boolean value) {
        if (!initialized) return false;
        remember();
        current = current.withOptions(current.suppressNotifications(), value);
        return true;
    }

    /** One undoable import edit. Authentication remains governed by the saved policy. */
    public boolean applyImported(HiddenConfig imported) {
        if (!initialized || imported == null || imported.authenticate() != current.authenticate()) return false;
        if (current.equals(imported)) return true;
        remember();
        current = imported;
        return true;
    }

    private boolean setPrivacy(boolean background, boolean screenOff, int timeout, boolean auth) {
        if (!initialized) return false;
        HiddenConfig next = current.withPrivacy(background, screenOff, timeout, auth);
        if (next.equals(current)) return true;
        remember();
        current = next;
        return true;
    }
    public boolean setConcealOnBackground(boolean value) {
        return setPrivacy(value, current.concealOnScreenOff(), current.revealTimeoutMs(), current.authenticate());
    }
    public boolean setConcealOnScreenOff(boolean value) {
        return setPrivacy(current.concealOnBackground(), value, current.revealTimeoutMs(), current.authenticate());
    }
    public boolean setRevealTimeoutMs(int value) {
        return setPrivacy(current.concealOnBackground(), current.concealOnScreenOff(), value, current.authenticate());
    }
    public boolean setAuthenticate(boolean value) {
        return setPrivacy(current.concealOnBackground(), current.concealOnScreenOff(), current.revealTimeoutMs(), value);
    }

    public boolean setHidden(DialogKey key, boolean hidden, long owner) {
        if (!initialized || key == null || owner <= 0) return false;
        remember();
        current = current.withSelection(key, hidden, owner);
        return true;
    }

    public boolean setHiddenBatch(List<CatalogEntry> entries, boolean hidden) {
        if (!initialized || entries == null || entries.isEmpty()) return false;
        remember();
        for (CatalogEntry entry : entries) {
            Long owner = hidden && entry.ownerId() > 0 ? entry.ownerId() : null;
            current = current.withSelection(entry.key(), hidden, owner);
        }
        return true;
    }

    public boolean rebind(DialogKey key, long expectedOwner, AccountInventory inventory) {
        if (!initialized || key == null || inventory == null || !current.isHidden(key)
                || !inventory.complete() || !inventory.matches(key.account(), expectedOwner)) return false;
        return setHidden(key, true, expectedOwner);
    }

    public boolean canSaveAgainst(AccountInventory inventory) {
        if (!initialized || inventory == null) return false;
        for (Map.Entry<DialogKey, Long> binding : current.bindings().entrySet()) {
            Long old = baseline.boundOwner(binding.getKey());
            if (!binding.getValue().equals(old)
                    && (!inventory.complete() || !inventory.matches(binding.getKey().account(), binding.getValue()))) return false;
        }
        return true;
    }

    public int undoSize() { return undo.size(); }
    public boolean undo() {
        if (undo.isEmpty()) return false;
        current = undo.removeLast();
        return true;
    }
    private void remember() {
        if (undo.size() == 50) undo.removeFirst();
        undo.addLast(current);
    }

    public boolean markSaved(boolean persisted) {
        if (!initialized) return false;
        if (!persisted) {
            requireVerifiedBaseline = true;
            return false;
        }
        baseline = current;
        undo.clear();
        return true;
    }

    public void discard() {
        if (initialized) { current = baseline; undo.clear(); }
    }

    public State snapshot() {
        return new State(initialized, encoded(baseline), baseline.suppressNotifications(),
                baseline.localPremium(), encoded(current), current.suppressNotifications(),
                current.localPremium(), requireVerifiedBaseline, encodeBindings(baseline), encodeBindings(current),
                baseline.concealOnBackground(), baseline.concealOnScreenOff(), baseline.revealTimeoutMs(), baseline.authenticate(),
                current.concealOnBackground(), current.concealOnScreenOff(), current.revealTimeoutMs(), current.authenticate());
    }

    public static ConfigurationDraft restore(State state) {
        ConfigurationDraft draft = new ConfigurationDraft();
        if (state != null && state.initialized) {
            draft.baseline = decode(state.baselineKeys, state.baselineBindings,
                    state.baselineNotifications, state.baselinePremium).withPrivacy(
                            state.baselineBackground, state.baselineScreenOff, state.baselineTimeout, state.baselineAuth);
            draft.current = decode(state.currentKeys, state.currentBindings,
                    state.currentNotifications, state.currentPremium).withPrivacy(
                            state.currentBackground, state.currentScreenOff, state.currentTimeout, state.currentAuth);
            draft.requireVerifiedBaseline = state.requireVerifiedBaseline;
            draft.initialized = true;
        }
        return draft;
    }

    private static Map<String, Long> encodeBindings(HiddenConfig config) {
        Map<String, Long> encoded = new HashMap<>();
        for (Map.Entry<DialogKey, Long> entry : config.bindings().entrySet())
            encoded.put(entry.getKey().toString(), entry.getValue());
        return encoded;
    }
    private static HiddenConfig decode(Set<String> keys, Map<String, Long> encodedBindings,
            boolean notifications, boolean premium) {
        Map<DialogKey, Long> bindings = new HashMap<>();
        if (encodedBindings != null) for (Map.Entry<String, Long> entry : encodedBindings.entrySet())
            DialogKey.tryParse(entry.getKey()).ifPresent(key -> {
                if (entry.getValue() != null && entry.getValue() > 0) bindings.put(key, entry.getValue());
            });
        Set<DialogKey> unbound = new HashSet<>();
        for (String key : keys) DialogKey.tryParse(key).ifPresent(unbound::add);
        unbound.removeAll(bindings.keySet());
        return HiddenConfig.fromBindings(bindings, unbound, notifications, premium);
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
        private final Map<String, Long> baselineBindings;
        private final Map<String, Long> currentBindings;
        private final boolean baselineBackground, baselineScreenOff, baselineAuth;
        private final int baselineTimeout;
        private final boolean currentBackground, currentScreenOff, currentAuth;
        private final int currentTimeout;

        private State(boolean initialized, Set<String> baselineKeys, boolean baselineNotifications,
                boolean baselinePremium, Set<String> currentKeys, boolean currentNotifications,
                boolean currentPremium, boolean requireVerifiedBaseline,
                Map<String, Long> baselineBindings, Map<String, Long> currentBindings,
                boolean baselineBackground, boolean baselineScreenOff, int baselineTimeout, boolean baselineAuth,
                boolean currentBackground, boolean currentScreenOff, int currentTimeout, boolean currentAuth) {
            this.initialized = initialized;
            this.baselineKeys = new HashSet<>(baselineKeys);
            this.baselineNotifications = baselineNotifications;
            this.baselinePremium = baselinePremium;
            this.currentKeys = new HashSet<>(currentKeys);
            this.currentNotifications = currentNotifications;
            this.currentPremium = currentPremium;
            this.requireVerifiedBaseline = requireVerifiedBaseline;
            this.baselineBindings = new HashMap<>(baselineBindings);
            this.currentBindings = new HashMap<>(currentBindings);
            this.baselineBackground = baselineBackground;
            this.baselineScreenOff = baselineScreenOff;
            this.baselineTimeout = baselineTimeout;
            this.baselineAuth = baselineAuth;
            this.currentBackground = currentBackground;
            this.currentScreenOff = currentScreenOff;
            this.currentTimeout = currentTimeout;
            this.currentAuth = currentAuth;
        }
    }
}
