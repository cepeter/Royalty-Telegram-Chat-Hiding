package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;

public final class ConfigurationDraftTest {
    private static final DialogKey ONE = DialogKey.of(0, 10);
    private static final DialogKey TWO = DialogKey.of(1, 20);

    @Test public void unavailableSettingsCannotBeEditedOrSaved() {
        ConfigurationDraft draft = new ConfigurationDraft();
        assertFalse(draft.setHidden(ONE, true));
        assertFalse(draft.canSave());
        assertFalse(draft.markSaved(true));
    }

    @Test public void refreshKeepsEditsWhileUpdatingSavedBaseline() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.fromStrings(Collections.singleton("0:10"), false));
        draft.setHidden(TWO, true);
        draft.setSuppressNotifications(true);
        draft.loadSaved(HiddenConfig.fromStrings(Collections.singleton("0:10"), false));
        assertTrue(draft.current().isHidden(TWO));
        assertTrue(draft.current().suppressNotifications());
        assertTrue(draft.dirty());
    }

    @Test public void failedSaveRetainsEditAndSuccessfulSaveAdvancesBaseline() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.empty());
        draft.setHidden(ONE, true);
        assertFalse(draft.markSaved(false));
        assertTrue(draft.dirty());
        assertTrue(draft.current().isHidden(ONE));
        assertTrue(draft.markSaved(true));
        assertFalse(draft.dirty());
        assertTrue(draft.baseline().isHidden(ONE));
    }

    @Test public void discardRestoresLatestSavedBaseline() {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.fromStrings(Collections.singleton("0:10"), false));
        draft.setHidden(TWO, true);
        draft.loadSaved(HiddenConfig.fromStrings(Collections.singleton("0:10"), true));
        draft.discard();
        assertFalse(draft.current().isHidden(TWO));
        assertTrue(draft.current().suppressNotifications());
        assertFalse(draft.dirty());
    }

    @Test public void dirtyDraftSurvivesSerializableRestoration() throws Exception {
        ConfigurationDraft draft = new ConfigurationDraft();
        draft.loadSaved(HiddenConfig.fromStrings(new HashSet<>(Collections.singleton("0:10")), false));
        draft.setHidden(TWO, true);
        draft.setLocalPremium(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(draft.snapshot());
        }
        ConfigurationDraft.State state;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            state = (ConfigurationDraft.State) input.readObject();
        }
        ConfigurationDraft restored = ConfigurationDraft.restore(state);
        assertTrue(restored.initialized());
        assertTrue(restored.dirty());
        assertTrue(restored.current().isHidden(TWO));
        assertTrue(restored.current().localPremium());
        assertFalse(restored.baseline().isHidden(TWO));
    }
}
