package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class CatalogSelectionTest {
    private static final CatalogEntry ALICE = new CatalogEntry(DialogKey.of(0, 10), "Alice", 101);
    private static final CatalogEntry ALEX = new CatalogEntry(DialogKey.of(1, 20), "Alex", 202);
    private static final CatalogEntry BOB = new CatalogEntry(DialogKey.of(0, 30), "Bob", 101);

    @Test public void searchAccountAndHiddenOnlyCompose() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.empty());
        draft.setHidden(ALEX.key(), true, 202);
        List<CatalogEntry> all = Arrays.asList(ALICE, ALEX, BOB);
        assertEquals(1, CatalogSelection.filter(all, draft.current(), "al", 1, true).size());
        assertEquals(ALEX.key(), CatalogSelection.filter(all, draft.current(), "al", 1, true).get(0).key());
        assertTrue(CatalogSelection.filter(all, draft.current(), "al", 0, true).isEmpty());
        assertEquals(1, CatalogSelection.selectedCount(draft.current()));
    }

    @Test public void selectMatchingOnlyChangesVisibleMatchesAndUndoIsBounded() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.empty());
        List<CatalogEntry> all = Arrays.asList(ALICE, ALEX, BOB);
        CatalogSelection.selectMatching(draft, CatalogSelection.filter(all, draft.current(), "al", 0, false), true);
        assertTrue(draft.current().isHidden(ALICE.key()));
        assertFalse(draft.current().isHidden(ALEX.key()));
        assertFalse(draft.current().isHidden(BOB.key()));
        assertTrue(draft.undo());
        assertFalse(draft.current().isHidden(ALICE.key()));
        for (int index = 0; index < 55; index++) draft.setSuppressNotifications(index % 2 == 0);
        assertEquals(50, draft.undoSize());
    }
}
