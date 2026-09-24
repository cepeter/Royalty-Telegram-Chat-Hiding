package io.github.cepeter.royalty.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.util.HashSet;
import java.util.Set;

public final class ConfigStore {
    public static final String PREFERENCES_NAME = "config";
    public static final String HIDDEN_DIALOGS = "hidden_dialogs";
    public static final String SUPPRESS_NOTIFICATIONS = "suppress_notifications";

    private ConfigStore() {}

    @SuppressWarnings("deprecation")
    @SuppressLint("WorldReadableFiles")
    public static SharedPreferences open(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_WORLD_READABLE);
    }

    public static HiddenConfig load(Context context) {
        SharedPreferences preferences = open(context);
        Set<String> stored = preferences.getStringSet(HIDDEN_DIALOGS, java.util.Collections.emptySet());
        Set<String> snapshot = stored == null ? java.util.Collections.emptySet() : new HashSet<>(stored);
        return HiddenConfig.fromStrings(
                snapshot,
                preferences.getBoolean(SUPPRESS_NOTIFICATIONS, false));
    }

    @SuppressLint("ApplySharedPref")
    public static boolean save(
            Context context, Set<DialogKey> hiddenDialogs, boolean suppressNotifications) {
        Set<String> encoded = new HashSet<>();
        for (DialogKey key : hiddenDialogs) {
            encoded.add(key.toString());
        }
        return open(context)
                .edit()
                .putStringSet(HIDDEN_DIALOGS, encoded)
                .putBoolean(SUPPRESS_NOTIFICATIONS, suppressNotifications)
                .commit();
    }
}
