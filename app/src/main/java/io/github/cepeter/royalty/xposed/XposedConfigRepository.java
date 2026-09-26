package io.github.cepeter.royalty.xposed;

import android.content.SharedPreferences;
import io.github.cepeter.royalty.config.ConfigStore;
import io.github.cepeter.royalty.core.HiddenConfig;

public final class XposedConfigRepository {
    private final SharedPreferences preferences;

    public XposedConfigRepository(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public HiddenConfig current() {
        return ConfigStore.load(preferences);
    }

    public boolean localPremiumEnabled() {
        return preferences.getBoolean(ConfigStore.LOCAL_PREMIUM, false);
    }
}
