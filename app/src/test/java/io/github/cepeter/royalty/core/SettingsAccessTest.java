package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class SettingsAccessTest {
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
