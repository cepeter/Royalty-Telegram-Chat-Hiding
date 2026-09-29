package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SettingsAccessTest {
    @Test public void unsavedDraftCannotKeepAccessOpenAfterBackground() {
        HiddenConfig saved = HiddenConfig.empty().withPrivacy(false, false, 0, true);
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(saved);
        SettingsAccess gate = new SettingsAccess();
        gate.learn(draft.baseline());
        gate.authenticated();
        assertTrue(draft.setAuthenticate(false));
        assertFalse(draft.current().authenticate());
        gate.background();
        gate.learn(draft.baseline());
        assertFalse(gate.allowed());
    }

    @Test public void unknownPolicyAndSavedAuthKeepProtectedSettingsClosed() {
        SettingsAccess gate = new SettingsAccess();
        assertFalse(gate.allowed());
        gate.learn(HiddenConfig.empty().withPrivacy(false, false, 0, true));
        assertFalse(gate.allowed());
        gate.authenticated();
        assertTrue(gate.allowed());
        gate.background();
        assertFalse(gate.allowed());
        gate.learn(HiddenConfig.empty());
        assertTrue(gate.allowed());
        gate.learn(HiddenConfig.empty().withPrivacy(false, false, 0, true));
        assertFalse(gate.allowed());
    }
}
