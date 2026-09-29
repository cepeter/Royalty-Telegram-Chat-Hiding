package io.github.cepeter.royalty;

import static org.junit.Assert.*;
import io.github.cepeter.royalty.core.*;
import java.util.*;
import org.junit.Test;

public final class SettingsDraftControlsTest {
    @Test public void privacyUndoRepaintsControlsWithoutListenerEditsAndPreservesFilteredSelection() {
        ConfigurationDraft draft = new ConfigurationDraft();
        DialogKey selectedKey = DialogKey.of(2, 7);
        draft.loadSaved(HiddenConfig.fromBindings(Collections.singletonMap(selectedKey, 101L),
                Collections.emptySet(), false, false));
        SettingsDraftControls binding = new SettingsDraftControls();
        List<CatalogEntry> catalog = Arrays.asList(new CatalogEntry(selectedKey, "query", 101),
                new CatalogEntry(DialogKey.of(1, 8), "query", 202),
                new CatalogEntry(DialogKey.of(2, 9), "other", 101));
        class Controls implements SettingsDraftControls.Controls {
            boolean notifications, premium, background, screenOff, authentication;
            int timeout, undo, selected; List<CatalogEntry> visible;
            String search = "query"; int account = 2; boolean hiddenOnly = true;
            public void notifications(boolean v) { notifications = v; }
            public void premium(boolean v) { premium = v; }
            public void background(boolean v) { background = v; }
            public void screenOff(boolean v) { screenOff = v; }
            public void timeout(int v) { timeout = v; }
            public void authentication(boolean v) {
                authentication = v;
                // Android Switch.setChecked dispatches listeners during programmatic paint.
                binding.edit(() -> draft.setAuthenticate(!v), this::paint);
            }
            void paint() {
                binding.render(draft, this, () -> {
                    undo = draft.undoSize(); selected = CatalogSelection.selectedCount(draft.current());
                    visible = CatalogSelection.filter(catalog, draft.current(), search, account, hiddenOnly);
                });
            }
        }
        Controls controls = new Controls(); controls.paint();
        binding.edit(() -> draft.setAuthenticate(true), controls::paint);
        assertTrue(controls.authentication); assertEquals(1, controls.undo);
        binding.edit(() -> draft.setRevealTimeoutMs(60000), controls::paint);
        assertEquals(60000, controls.timeout); assertEquals(2, controls.undo);
        binding.edit(() -> draft.setConcealOnBackground(true), controls::paint);
        binding.edit(() -> draft.setConcealOnScreenOff(true), controls::paint);
        binding.edit(() -> draft.setSuppressNotifications(true), controls::paint);
        binding.edit(() -> draft.setLocalPremium(true), controls::paint);
        assertTrue(controls.background); assertTrue(controls.screenOff);
        assertTrue(controls.notifications); assertTrue(controls.premium);
        for (int i = 0; i < 4; i++) binding.edit(draft::undo, controls::paint);
        assertFalse(controls.background); assertFalse(controls.screenOff);
        assertFalse(controls.notifications); assertFalse(controls.premium);
        binding.edit(draft::undo, controls::paint);
        assertEquals(0, controls.timeout); assertTrue(controls.authentication);
        binding.edit(draft::undo, controls::paint);
        assertFalse(controls.authentication); assertEquals(0, controls.undo);
        assertTrue(draft.markSaved(true));
        assertEquals(controls.authentication, draft.baseline().authenticate());
        assertEquals(controls.timeout, draft.baseline().revealTimeoutMs());
        assertEquals("query", controls.search); assertEquals(2, controls.account); assertTrue(controls.hiddenOnly);
        assertEquals(1, controls.selected); assertEquals(Collections.singletonList(catalog.get(0)), controls.visible);
    }
}
