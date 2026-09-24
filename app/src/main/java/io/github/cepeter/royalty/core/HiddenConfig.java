package io.github.cepeter.royalty.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class HiddenConfig {
    private static final HiddenConfig EMPTY = new HiddenConfig(Collections.emptySet(), false);

    private final Set<DialogKey> hiddenDialogs;
    private final boolean suppressNotifications;

    private HiddenConfig(Set<DialogKey> hiddenDialogs, boolean suppressNotifications) {
        this.hiddenDialogs = Collections.unmodifiableSet(new HashSet<>(hiddenDialogs));
        this.suppressNotifications = suppressNotifications;
    }

    public static HiddenConfig empty() {
        return EMPTY;
    }

    public static HiddenConfig fromStrings(Set<String> values, boolean suppressNotifications) {
        if (values == null || values.isEmpty()) {
            return suppressNotifications
                    ? new HiddenConfig(Collections.emptySet(), true)
                    : EMPTY;
        }

        Set<DialogKey> parsed = new HashSet<>();
        for (String value : values) {
            DialogKey.tryParse(value).ifPresent(parsed::add);
        }
        return new HiddenConfig(parsed, suppressNotifications);
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
}
