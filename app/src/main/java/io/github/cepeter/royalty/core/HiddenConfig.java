package io.github.cepeter.royalty.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class HiddenConfig {
    private static final HiddenConfig EMPTY = new HiddenConfig(Collections.emptySet(), false, false);

    private final Set<DialogKey> hiddenDialogs;
    private final boolean suppressNotifications;
    private final boolean localPremium;

    private HiddenConfig(Set<DialogKey> hiddenDialogs, boolean suppressNotifications, boolean localPremium) {
        this.hiddenDialogs = Collections.unmodifiableSet(new HashSet<>(hiddenDialogs));
        this.suppressNotifications = suppressNotifications;
        this.localPremium = localPremium;
    }

    public static HiddenConfig empty() {
        return EMPTY;
    }

    public static HiddenConfig fromStrings(Set<String> values, boolean suppressNotifications) {
        return fromStrings(values, suppressNotifications, false);
    }

    public static HiddenConfig fromStrings(
            Set<String> values, boolean suppressNotifications, boolean localPremium) {
        if (values == null || values.isEmpty()) {
            if (!suppressNotifications && !localPremium) {
                return EMPTY;
            }
            return new HiddenConfig(Collections.emptySet(), suppressNotifications, localPremium);
        }

        Set<DialogKey> parsed = new HashSet<>();
        for (String value : values) {
            DialogKey.tryParse(value).ifPresent(parsed::add);
        }
        return new HiddenConfig(parsed, suppressNotifications, localPremium);
    }

    public boolean isHidden(DialogKey key) {
        return key != null && hiddenDialogs.contains(key);
    }

    public Set<DialogKey> hiddenDialogs() {
        return hiddenDialogs;
    }

    public boolean suppressNotifications() {
        return suppressNotifications;
    }

    public boolean localPremium() {
        return localPremium;
    }
}
