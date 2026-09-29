package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ProtectedModalControllerTest {
    @Test public void relockClosesEveryOpenModalAndRevokesItsCallbacksAfterUnlock() {
        SettingsAccess gate = new SettingsAccess();
        gate.learn(HiddenConfig.empty().withPrivacy(false, false, 0, true));
        gate.authenticated();
        ProtectedModalController modals = new ProtectedModalController(gate);
        Object binding = new Object();
        Object error = new Object();
        int[] closed = {0};
        assertTrue(modals.open(binding, () -> closed[0]++));
        assertTrue(modals.open(error, () -> closed[0]++));
        gate.background();
        modals.closeAll();
        assertEquals(2, closed[0]);
        assertFalse(modals.consume(binding));
        gate.authenticated();
        assertFalse(modals.consume(binding));
        Object fresh = new Object();
        assertTrue(modals.open(fresh, () -> closed[0]++));
        assertTrue(modals.consume(fresh));
        assertFalse(modals.consume(fresh));
    }

    @Test public void destroyedActivityRevokesDialogEvenIfRotationKeepsSettingsUnlocked() {
        SettingsAccess gate = new SettingsAccess();
        gate.learn(HiddenConfig.empty().withPrivacy(false, false, 0, true));
        gate.authenticated();
        ProtectedModalController modals = new ProtectedModalController(gate);
        Object token = new Object();
        int[] closed = {0};
        assertTrue(modals.open(token, () -> closed[0]++));
        modals.closeAll();
        assertTrue(gate.allowed());
        assertEquals(1, closed[0]);
        assertFalse(modals.consume(token));
    }

    @Test public void unknownPolicyNeverOpensProtectedModal() {
        SettingsAccess gate = new SettingsAccess();
        ProtectedModalController modals = new ProtectedModalController(gate);
        int[] closed = {0};
        Object token = new Object();
        assertFalse(modals.open(token, () -> closed[0]++));
        assertEquals(1, closed[0]);
        assertFalse(modals.consume(token));
    }
}
