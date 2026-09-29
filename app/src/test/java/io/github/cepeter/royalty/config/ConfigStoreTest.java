package io.github.cepeter.royalty.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.ConfigurationDraft;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class ConfigStoreTest {
    @Test public void boundOwnerRoundTripsAndLegacyRemainsUnbound() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences preferences = preferences(values);
        DialogKey key = DialogKey.of(0, 42);
        HiddenConfig bound = HiddenConfig.fromBindings(Collections.singletonMap(key, 101L),
                Collections.emptySet(), false, false);
        assertTrue(ConfigStore.save(preferences, bound));
        assertTrue(ConfigStore.load(preferences).isHidden(key));
        assertTrue(ConfigStore.load(preferences).eligible(new io.github.cepeter.royalty.core.AccountInventory(
                Collections.singletonMap(0, new io.github.cepeter.royalty.core.AccountInventory.Owner(101, "A")), true, "")).isHidden(key));
        values.remove(ConfigStore.OWNER_BINDINGS);
        assertTrue(ConfigStore.load(preferences).unbound().contains(key));
    }

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

    @Test
    public void memoryMutatingFailedSaveCannotBecomeDraftBaselineOnRefreshOrReconnect() {
        Map<String, Object> values = new HashMap<>();
        boolean[] failCommit = {false};
        SharedPreferences preferences = preferences(values, failCommit);
        DialogKey savedKey = DialogKey.of(0, 42);
        DialogKey editedKey = DialogKey.of(1, 43);
        assertTrue(ConfigStore.save(preferences, Collections.singleton(savedKey), false, false));
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(ConfigStore.load(preferences));
        draft.setHidden(editedKey, true);

        failCommit[0] = true;
        HiddenConfig edit = draft.current();
        boolean persisted = ConfigStore.save(preferences, edit.hiddenDialogs(),
                edit.suppressNotifications(), edit.localPremium());
        assertFalse(persisted);
        assertFalse(draft.markSaved(persisted));
        assertTrue(ConfigStore.load(preferences).isHidden(editedKey));
        draft.loadSaved(ConfigStore.load(preferences));
        draft.loadSaved(ConfigStore.load(preferences(values, failCommit)));
        assertTrue(draft.dirty());
        assertFalse(draft.baseline().isHidden(editedKey));
        draft.discard();
        assertTrue(draft.current().isHidden(savedKey));
        assertFalse(draft.current().isHidden(editedKey));
    }

    @Test
    public void olderFailedPreferenceImageCannotBecomeSavedAfterSecondFailure() {
        Map<String, Object> values = new HashMap<>();
        boolean[] failCommit = {false};
        SharedPreferences preferences = preferences(values, failCommit);
        DialogKey saved = DialogKey.of(0, 42);
        DialogKey edited = DialogKey.of(1, 43);
        assertTrue(ConfigStore.save(preferences, Collections.singleton(saved), false, false));
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(ConfigStore.load(preferences));

        failCommit[0] = true;
        draft.setHidden(edited, true);
        HiddenConfig firstAttempt = draft.current();
        assertFalse(ConfigStore.save(preferences, firstAttempt.hiddenDialogs(), false, false));
        draft.markSaved(false);
        Map<String, Object> olderImage = new HashMap<>(values);
        draft.setSuppressNotifications(true);
        HiddenConfig secondAttempt = draft.current();
        assertFalse(ConfigStore.save(preferences, secondAttempt.hiddenDialogs(), true, false));
        draft.markSaved(false);

        ConfigurationDraft recreated = ConfigurationDraft.restore(draft.snapshot());
        recreated.loadSaved(ConfigStore.load(preferences(olderImage, failCommit)));
        recreated.loadSaved(ConfigStore.load(preferences(values, failCommit)));
        assertTrue(recreated.dirty());
        assertFalse(recreated.baseline().isHidden(edited));
        recreated.discard();
        assertTrue(recreated.current().isHidden(saved));
        assertFalse(recreated.current().isHidden(edited));

        failCommit[0] = false;
        recreated.setHidden(edited, true);
        recreated.setSuppressNotifications(true);
        HiddenConfig retry = recreated.current();
        assertTrue(ConfigStore.save(preferences, retry.hiddenDialogs(),
                retry.suppressNotifications(), retry.localPremium()));
        assertTrue(recreated.markSaved(true));
        recreated.loadSaved(ConfigStore.load(preferences(olderImage, failCommit)));
        assertTrue(recreated.baseline().isHidden(edited));
        assertTrue(recreated.baseline().suppressNotifications());
    }

    private static SharedPreferences preferences(Map<String, Object> values) {
        return preferences(values, new boolean[] {false});
    }

    private static SharedPreferences preferences(Map<String, Object> values, boolean[] failCommit) {
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
                            return !failCommit[0];
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
