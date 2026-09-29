package io.github.cepeter.royalty.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Saved selections keep unbound legacy keys for review; runtime projection is owner-safe. */
public final class HiddenConfig {
    private static final HiddenConfig EMPTY = new HiddenConfig(Collections.emptyMap(), Collections.emptySet(), false, false, false, false, 0, false);
    private final Map<DialogKey, Long> bindings;
    private final Set<DialogKey> unbound;
    private final Set<DialogKey> hiddenDialogs;
    private final boolean suppressNotifications;
    private final boolean localPremium;
    private final boolean concealOnBackground;
    private final boolean concealOnScreenOff;
    private final int revealTimeoutMs;
    private final boolean authenticate;

    private HiddenConfig(Map<DialogKey, Long> bindings, Set<DialogKey> unbound,
            boolean suppressNotifications, boolean localPremium, boolean background,
            boolean screenOff, int timeout, boolean authenticate) {
        this.bindings = Collections.unmodifiableMap(new HashMap<>(bindings));
        this.unbound = Collections.unmodifiableSet(new HashSet<>(unbound));
        Set<DialogKey> all = new HashSet<>(bindings.keySet());
        all.addAll(unbound);
        this.hiddenDialogs = Collections.unmodifiableSet(all);
        this.suppressNotifications = suppressNotifications;
        this.localPremium = localPremium;
        this.concealOnBackground = background;
        this.concealOnScreenOff = screenOff;
        this.revealTimeoutMs = timeout;
        this.authenticate = authenticate;
    }
    public static HiddenConfig empty() { return EMPTY; }
    public static HiddenConfig fromBindings(Map<DialogKey, Long> bindings, Set<DialogKey> unbound,
            boolean notifications, boolean premium) {
        for (Map.Entry<DialogKey, Long> entry : bindings.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0)
                throw new IllegalArgumentException("invalid binding");
        }
        Set<DialogKey> legacy = new HashSet<>(unbound);
        legacy.removeAll(bindings.keySet());
        return new HiddenConfig(bindings, legacy, notifications, premium, false, false, 0, false);
    }
    public static HiddenConfig fromBindings(Map<DialogKey, Long> bindings, Set<DialogKey> unbound,
            boolean notifications, boolean premium, boolean background, boolean screenOff,
            int timeout, boolean authenticate) {
        HiddenConfig base = fromBindings(bindings, unbound, notifications, premium);
        return base.withPrivacy(background, screenOff, timeout, authenticate);
    }
    public HiddenConfig withPrivacy(boolean background, boolean screenOff, int timeout, boolean auth) {
        if (timeout != 0 && timeout != 30000 && timeout != 60000 && timeout != 300000)
            throw new IllegalArgumentException("unsupported reveal timeout");
        return new HiddenConfig(bindings, unbound, suppressNotifications, localPremium,
                background, screenOff, timeout, auth);
    }
    public static HiddenConfig fromStrings(Set<String> values, boolean notifications) {
        return fromStrings(values, notifications, false);
    }
    public static HiddenConfig fromStrings(Set<String> values, boolean notifications, boolean premium) {
        Set<DialogKey> parsed = new HashSet<>();
        if (values != null) for (String value : values) DialogKey.tryParse(value).ifPresent(parsed::add);
        return fromBindings(Collections.emptyMap(), parsed, notifications, premium);
    }
    public HiddenConfig withSelection(DialogKey key, boolean hidden, Long owner) {
        Map<DialogKey, Long> nextBindings = new HashMap<>(bindings);
        Set<DialogKey> nextUnbound = new HashSet<>(unbound);
        if (!hidden) { nextBindings.remove(key); nextUnbound.remove(key); }
        else if (owner != null && owner > 0) { nextBindings.put(key, owner); nextUnbound.remove(key); }
        else if (!nextBindings.containsKey(key)) nextUnbound.add(key);
        return fromBindings(nextBindings, nextUnbound, suppressNotifications, localPremium)
                .withPrivacy(concealOnBackground, concealOnScreenOff, revealTimeoutMs, authenticate);
    }
    public HiddenConfig withOptions(boolean notifications, boolean premium) {
        return fromBindings(bindings, unbound, notifications, premium)
                .withPrivacy(concealOnBackground, concealOnScreenOff, revealTimeoutMs, authenticate);
    }
    public HiddenConfig eligible(AccountInventory inventory) {
        if (inventory == null || !inventory.complete())
            return fromBindings(Collections.emptyMap(), Collections.emptySet(),
                    suppressNotifications, localPremium).withPrivacy(
                            concealOnBackground, concealOnScreenOff, revealTimeoutMs, authenticate);
        Map<DialogKey, Long> eligible = new HashMap<>();
        for (Map.Entry<DialogKey, Long> entry : bindings.entrySet()) {
            if (inventory.matches(entry.getKey().account(), entry.getValue())) eligible.put(entry.getKey(), entry.getValue());
        }
        return fromBindings(eligible, Collections.emptySet(), suppressNotifications, localPremium)
                .withPrivacy(concealOnBackground, concealOnScreenOff, revealTimeoutMs, authenticate);
    }
    public boolean isHidden(DialogKey key) { return key != null && hiddenDialogs.contains(key); }
    public Set<DialogKey> hiddenDialogs() { return hiddenDialogs; }
    public Map<DialogKey, Long> bindings() { return bindings; }
    public Set<DialogKey> unbound() { return unbound; }
    public Long boundOwner(DialogKey key) { return bindings.get(key); }
    public boolean suppressNotifications() { return suppressNotifications; }
    public boolean localPremium() { return localPremium; }
    public boolean concealOnBackground() { return concealOnBackground; }
    public boolean concealOnScreenOff() { return concealOnScreenOff; }
    public int revealTimeoutMs() { return revealTimeoutMs; }
    public boolean authenticate() { return authenticate; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof HiddenConfig)) return false;
        HiddenConfig that = (HiddenConfig) other;
        return bindings.equals(that.bindings) && unbound.equals(that.unbound)
                && suppressNotifications == that.suppressNotifications && localPremium == that.localPremium
                && concealOnBackground == that.concealOnBackground
                && concealOnScreenOff == that.concealOnScreenOff
                && revealTimeoutMs == that.revealTimeoutMs && authenticate == that.authenticate;
    }
    @Override public int hashCode() {
        int hash = bindings.hashCode();
        hash = 31 * hash + unbound.hashCode();
        hash = 31 * hash + Boolean.hashCode(suppressNotifications);
        hash = 31 * hash + Boolean.hashCode(localPremium);
        hash = 31 * hash + Boolean.hashCode(concealOnBackground);
        hash = 31 * hash + Boolean.hashCode(concealOnScreenOff);
        hash = 31 * hash + revealTimeoutMs;
        return 31 * hash + Boolean.hashCode(authenticate);
    }
}
