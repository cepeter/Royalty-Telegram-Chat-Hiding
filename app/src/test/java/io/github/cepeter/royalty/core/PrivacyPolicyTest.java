package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import org.junit.Test;

public final class PrivacyPolicyTest {
    @Test public void policyDefaultsOffAndSurvivesSelectionEligibilityUndoAndRestore() {
        HiddenConfig original = HiddenConfig.empty();
        assertFalse(original.authenticate());
        assertFalse(original.concealOnBackground());
        assertFalse(original.concealOnScreenOff());
        assertEquals(0, original.revealTimeoutMs());
        HiddenConfig policy = original.withPrivacy(true, true, 60000, true);
        DialogKey key = DialogKey.of(0, 3);
        assertEquals(policy.withSelection(key, true, 5L).withOptions(true, false).revealTimeoutMs(), 60000);
        assertTrue(policy.eligible(AccountInventory.incomplete("pending")).authenticate());
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(policy);
        draft.setRevealTimeoutMs(30000);
        draft.setConcealOnBackground(false);
        draft.undo();
        assertTrue(draft.current().concealOnBackground());
        ConfigurationDraft restored = ConfigurationDraft.restore(draft.snapshot());
        assertEquals(30000, restored.current().revealTimeoutMs());
        restored.discard();
        assertEquals(60000, restored.current().revealTimeoutMs());
        assertTrue(restored.baseline().authenticate());
    }
    @Test public void onlySupportedTimeoutsAreAccepted() {
        try { HiddenConfig.empty().withPrivacy(false, false, 1000, false); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
