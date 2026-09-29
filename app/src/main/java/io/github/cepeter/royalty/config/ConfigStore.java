package io.github.cepeter.royalty.config;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigStore {
    public static final String PREFERENCES_NAME = "config";
    public static final String HIDDEN_DIALOGS = "hidden_dialogs";
    public static final String OWNER_BINDINGS = "owner_bindings";
    public static final String SUPPRESS_NOTIFICATIONS = "suppress_notifications";
    public static final String LOCAL_PREMIUM = "local_premium";

    private ConfigStore() {}

    public static HiddenConfig load(SharedPreferences preferences) {
        Set<String> stored = preferences.getStringSet(HIDDEN_DIALOGS, java.util.Collections.emptySet());
        Set<String> snapshot = stored == null ? java.util.Collections.emptySet() : new HashSet<>(stored);
        Set<String> storedBindings = preferences.getStringSet(OWNER_BINDINGS, java.util.Collections.emptySet());
        Map<DialogKey, Long> bindings = new HashMap<>();
        if (storedBindings != null) for (String value : storedBindings) {
            if (value == null) continue;
            int separator = value.lastIndexOf(':');
            if (separator < 0) continue;
            try {
                DialogKey key = DialogKey.parse(value.substring(0, separator));
                long owner = Long.parseLong(value.substring(separator + 1));
                if (owner > 0 && snapshot.contains(key.toString())) bindings.put(key, owner);
            } catch (IllegalArgumentException ignored) { /* malformed binding remains legacy */ }
        }
        Set<DialogKey> unbound = new HashSet<>();
        for (String value : snapshot) DialogKey.tryParse(value).ifPresent(unbound::add);
        unbound.removeAll(bindings.keySet());
        return HiddenConfig.fromBindings(bindings, unbound,
                preferences.getBoolean(SUPPRESS_NOTIFICATIONS, false),
                preferences.getBoolean(LOCAL_PREMIUM, false));
    }

    @SuppressLint("ApplySharedPref")
    public static boolean save(SharedPreferences preferences, HiddenConfig config) {
        Set<String> encoded = new HashSet<>();
        for (DialogKey key : config.hiddenDialogs()) encoded.add(key.toString());
        Set<String> bindings = new HashSet<>();
        for (Map.Entry<DialogKey, Long> entry : config.bindings().entrySet())
            bindings.add(entry.getKey() + ":" + entry.getValue());
        return preferences.edit().putStringSet(HIDDEN_DIALOGS, encoded)
                .putStringSet(OWNER_BINDINGS, bindings)
                .putBoolean(SUPPRESS_NOTIFICATIONS, config.suppressNotifications())
                .putBoolean(LOCAL_PREMIUM, config.localPremium()).commit();
    }

    @SuppressLint("ApplySharedPref")
    public static boolean save(
            SharedPreferences preferences,
            Set<DialogKey> hiddenDialogs,
            boolean suppressNotifications,
            boolean localPremium) {
        Set<String> encoded = new HashSet<>();
        for (DialogKey key : hiddenDialogs) {
            encoded.add(key.toString());
        }
        return preferences.edit()
                .putStringSet(OWNER_BINDINGS, java.util.Collections.emptySet())
                .putStringSet(HIDDEN_DIALOGS, encoded)
                .putBoolean(SUPPRESS_NOTIFICATIONS, suppressNotifications)
                .putBoolean(LOCAL_PREMIUM, localPremium)
                .commit();
    }
}
