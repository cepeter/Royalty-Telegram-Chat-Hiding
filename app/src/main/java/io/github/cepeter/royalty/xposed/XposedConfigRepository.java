package io.github.cepeter.royalty.xposed;

import android.content.SharedPreferences;
import io.github.cepeter.royalty.config.ConfigStore;
import io.github.cepeter.royalty.core.HiddenConfig;
import io.github.cepeter.royalty.core.AccountInventory;
import java.util.function.BiConsumer;

public final class XposedConfigRepository {
    private final SharedPreferences preferences;
    private UserConfigOwnerResolver resolver;
    private BiConsumer<String, String> ownerStatus;

    public XposedConfigRepository(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public void setOwnerResolver(UserConfigOwnerResolver resolver, BiConsumer<String, String> status) {
        this.resolver = resolver;
        this.ownerStatus = status;
    }

    public AccountInventory inventory() {
        AccountInventory inventory = resolver == null
                ? AccountInventory.incomplete("owner API not initialized") : resolver.read();
        if (ownerStatus != null) ownerStatus.accept(
                inventory.complete() ? "installed" : "runtime_error", inventory.detail());
        return inventory;
    }

    public HiddenConfig current() {
        return ConfigStore.load(preferences).eligible(inventory());
    }

    public boolean localPremiumEnabled() {
        return preferences.getBoolean(ConfigStore.LOCAL_PREMIUM, false);
    }
}
