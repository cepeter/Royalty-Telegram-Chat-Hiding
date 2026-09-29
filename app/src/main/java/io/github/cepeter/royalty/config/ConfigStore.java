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

    public static final String CONCEAL_BACKGROUND = "conceal_background";
    public static final String CONCEAL_SCREEN_OFF = "conceal_screen_off";
    public static final String REVEAL_TIMEOUT = "reveal_timeout_ms";
    public static final String AUTHENTICATE = "authenticate";
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
                preferences.getBoolean(LOCAL_PREMIUM, false),
                preferences.getBoolean(CONCEAL_BACKGROUND, false),
                preferences.getBoolean(CONCEAL_SCREEN_OFF, false),
                safeTimeout(preferences.getInt(REVEAL_TIMEOUT, 0)),
                preferences.getBoolean(AUTHENTICATE, false));
    }

    private static int safeTimeout(int value) {
        return value == 30000 || value == 60000 || value == 300000 ? value : 0;
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
                .putBoolean(LOCAL_PREMIUM, config.localPremium())
                .putBoolean(CONCEAL_BACKGROUND, config.concealOnBackground())
                .putBoolean(CONCEAL_SCREEN_OFF, config.concealOnScreenOff())
                .putInt(REVEAL_TIMEOUT, config.revealTimeoutMs())
                .putBoolean(AUTHENTICATE, config.authenticate()).commit();
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
