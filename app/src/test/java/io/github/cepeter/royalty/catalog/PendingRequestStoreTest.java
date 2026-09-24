package io.github.cepeter.royalty.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import org.junit.Test;

public final class PendingRequestStoreTest {
    @Test
    public void createsUniqueLowercase128BitNonces() {
        PendingRequestStore store = new PendingRequestStore(new HashMap<>());
        PendingRequestStore.Request first = store.create(1000);
        PendingRequestStore.Request second = store.create(1000);
        assertEquals(32, first.nonce().length());
        assertTrue(first.nonce().matches("[0-9a-f]{32}"));
        assertNotEquals(first.nonce(), second.nonce());
        assertEquals(1000 + CatalogProtocol.NONCE_LIFETIME_MS,
                first.expiresAtElapsedRealtime());
    }

    @Test
    public void acceptsUntilExpiryAndCanBeUsedMultipleTimes() {
        PendingRequestStore store = new PendingRequestStore(new HashMap<>());
        PendingRequestStore.Request request = store.create(1000);
        assertTrue(store.isActive(request.nonce(), 1001));
        assertTrue(store.isActive(request.nonce(), 1002));
        assertFalse(store.isActive(
                request.nonce(), request.expiresAtElapsedRealtime()));
    }

    @Test
    public void completionInvalidatesNonce() {
        PendingRequestStore store = new PendingRequestStore(new HashMap<>());
        PendingRequestStore.Request request = store.create(1000);
        store.complete(request.nonce());
        assertFalse(store.isActive(request.nonce(), 1001));
    }
}
