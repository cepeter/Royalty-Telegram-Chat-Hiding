package io.github.cepeter.royalty.xposed;

import android.os.SystemClock;
import de.robv.android.xposed.XSharedPreferences;
import io.github.cepeter.royalty.BuildConfig;
import io.github.cepeter.royalty.config.ConfigStore;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class XposedConfigRepository {
    private static final long RELOAD_INTERVAL_MS = 1000;

    private final XSharedPreferences preferences;
    private long nextReloadAt;
    private HiddenConfig cached = HiddenConfig.empty();

    public XposedConfigRepository() {
        preferences = new XSharedPreferences(BuildConfig.APPLICATION_ID, ConfigStore.PREFERENCES_NAME);
        preferences.makeWorldReadable();
    }

    public synchronized HiddenConfig current() {
        long now = SystemClock.elapsedRealtime();
        if (now < nextReloadAt) {
            return cached;
        }

        preferences.reload();
        Set<String> stored = preferences.getStringSet(
                ConfigStore.HIDDEN_DIALOGS, Collections.emptySet());
        Set<String> snapshot = stored == null ? Collections.emptySet() : new HashSet<>(stored);
        cached = HiddenConfig.fromStrings(
                snapshot,
                preferences.getBoolean(ConfigStore.SUPPRESS_NOTIFICATIONS, false));
        nextReloadAt = now + RELOAD_INTERVAL_MS;
        return cached;
    }
}
