package io.github.cepeter.royalty.config;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.util.HashSet;
import java.util.Set;

public final class ConfigStore {
    public static final String PREFERENCES_NAME = "config";
    public static final String HIDDEN_DIALOGS = "hidden_dialogs";
    public static final String SUPPRESS_NOTIFICATIONS = "suppress_notifications";
    public static final String LOCAL_PREMIUM = "local_premium";

    private ConfigStore() {}

    public static HiddenConfig load(SharedPreferences preferences) {
        Set<String> stored = preferences.getStringSet(HIDDEN_DIALOGS, java.util.Collections.emptySet());
        Set<String> snapshot = stored == null ? java.util.Collections.emptySet() : new HashSet<>(stored);
        return HiddenConfig.fromStrings(
                snapshot,
                preferences.getBoolean(SUPPRESS_NOTIFICATIONS, false),
                preferences.getBoolean(LOCAL_PREMIUM, false));
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
                .putStringSet(HIDDEN_DIALOGS, encoded)
                .putBoolean(SUPPRESS_NOTIFICATIONS, suppressNotifications)
                .putBoolean(LOCAL_PREMIUM, localPremium)
                .commit();
    }
}
