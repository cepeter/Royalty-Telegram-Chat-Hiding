package io.github.cepeter.royalty.catalog;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class CatalogUpdatesTest {
    @Test public void completionOfSupersededRequestDoesNotCancelCurrentRequest() {
        CatalogUpdates updates = new CatalogUpdates();
        List<CatalogUpdates.Event> received = new ArrayList<>();
        updates.subscribe(received::add);
        updates.begin("a", 16000);
        updates.begin("b", 17000);
        assertFalse(updates.complete("a", true));
        assertEquals("b", updates.activeNonce());
        assertTrue(received.isEmpty());
        assertTrue(updates.complete("b", false));
        assertNull(updates.activeNonce());
        assertEquals("b", received.get(0).nonce());
        assertFalse(received.get(0).success());
        assertFalse(updates.complete("b", true));
        assertEquals(1, received.size());
    }
}
