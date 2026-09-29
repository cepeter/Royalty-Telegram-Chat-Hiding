package io.github.cepeter.royalty.catalog;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class CatalogUpdatesTest {
    @Test public void failureEventPreservesCause() {
        CatalogUpdates updates = new CatalogUpdates();
        final String[] detail = {""};
        updates.subscribe(event -> detail[0] = event.detail());
        updates.begin("reason", 100);
        assertTrue(updates.complete("reason", false, "invalid account frame"));
        assertEquals("invalid account frame", detail[0]);
    }

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
