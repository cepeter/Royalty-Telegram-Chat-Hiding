package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class AccountBindingTest {
    private static final DialogKey KEY = DialogKey.of(0, 42);
    private static AccountInventory inventory(boolean complete, long owner) {
        Map<Integer, AccountInventory.Owner> owners = new HashMap<>();
        if (owner != 0) owners.put(0, new AccountInventory.Owner(owner, "Alice"));
        return new AccountInventory(owners, complete, complete ? "" : "config not loaded");
    }

    @Test public void replacementNeverInheritsBoundConcealment() {
        HiddenConfig config = HiddenConfig.fromBindings(Collections.singletonMap(KEY, 101L),
                Collections.emptySet(), true, false);
        assertTrue(config.eligible(inventory(true, 101)).isHidden(KEY));
        assertFalse(config.eligible(inventory(true, 202)).isHidden(KEY));
        assertFalse(config.eligible(inventory(false, 0)).isHidden(KEY));
        assertTrue(config.isHidden(KEY));
    }

    @Test public void partialInventoryCannotApplyAnyConcealment() {
        HiddenConfig config = HiddenConfig.fromBindings(Collections.singletonMap(KEY, 101L),
                Collections.emptySet(), false, false);
        assertFalse(config.eligible(inventory(false, 101)).isHidden(KEY));
    }

    @Test public void confirmedLogoutDiffersFromIncompleteInventory() {
        assertTrue(inventory(true, 0).confirmedAbsent(0));
        assertFalse(inventory(false, 0).confirmedAbsent(0));
    }

    @Test public void legacySelectionIsQuarantinedUntilExplicitReview() {
        HiddenConfig legacy = HiddenConfig.fromStrings(Collections.singleton("0:42"), false);
        assertFalse(legacy.eligible(inventory(true, 101)).isHidden(KEY));
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(legacy);
        assertTrue(draft.rebind(KEY, 101, inventory(true, 101)));
        assertEquals(Long.valueOf(101), draft.current().boundOwner(KEY));
        assertFalse(draft.rebind(KEY, 101, inventory(true, 202)));
    }

    @Test public void staleNewBindingCannotBeSavedAfterReplacement() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.empty());
        assertTrue(draft.setHidden(KEY, true, 101));
        assertFalse(draft.canSaveAgainst(inventory(true, 202)));
        assertTrue(draft.canSaveAgainst(inventory(true, 101)));
    }
}
