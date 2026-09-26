package io.github.cepeter.royalty.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class ConfigStoreTest {
    @Test
    public void localPremiumRoundTripsAndDefaultsOff() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences preferences = preferences(values);

        assertFalse(ConfigStore.load(preferences).localPremium());
        assertTrue(ConfigStore.save(
                preferences,
                Collections.singleton(DialogKey.of(0, 42)),
                false,
                true));

        HiddenConfig restored = ConfigStore.load(preferences);
        assertTrue(restored.localPremium());
        assertTrue(restored.isHidden(DialogKey.of(0, 42)));
    }

    private static SharedPreferences preferences(Map<String, Object> values) {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "putBoolean":
                        case "putStringSet":
                            values.put((String) args[0], args[1]);
                            return proxy;
                        case "commit":
                            return true;
                        case "apply":
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getBoolean":
                        case "getStringSet":
                            return values.getOrDefault((String) args[0], args[1]);
                        case "edit":
                            return editor;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
