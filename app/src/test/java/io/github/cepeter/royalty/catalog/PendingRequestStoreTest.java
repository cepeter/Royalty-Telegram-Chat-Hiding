package io.github.cepeter.royalty.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class PendingRequestStoreTest {
    /** Verifies that first use records boot zero and retains a new nonce on the same boot. */
    @Test
    public void firstUseRecordsBootAndPreservesNewNonceAcrossRecreation() {
        MemoryPreferences memory = new MemoryPreferences();
        PendingRequestStore store = new PendingRequestStore(memory.preferences(), 0);
        assertEquals(0, memory.values.get("boot_id"));
        PendingRequestStore.Request request = store.create(1000);
        assertEquals(0, memory.values.get("boot_id"));
        assertTrue(new PendingRequestStore(memory.preferences(), 0)
                .isActive(request.nonce(), 1001));
    }

    /** Verifies that a missing boot marker discards old nonces and records the current boot. */
    @Test
    public void missingBootIdDiscardsUnverifiablePersistedNonce() {
        MemoryPreferences memory = new MemoryPreferences();
        memory.values.put("old-nonce", 100000L);
        PendingRequestStore store = new PendingRequestStore(memory.preferences(), 7);
        assertFalse(store.isActive("old-nonce", 1000));
        assertFalse(memory.values.containsKey("old-nonce"));
        assertEquals(7, memory.values.get("boot_id"));
    }

    /** Verifies that a reboot invalidates persisted nonces despite their old expiry times. */
    @Test
    public void changedBootDiscardsNonceEvenBeforeItsOldExpiry() {
        MemoryPreferences memory = new MemoryPreferences();
        PendingRequestStore store = new PendingRequestStore(memory.preferences(), 7);
        PendingRequestStore.Request request = store.create(1000);
        assertFalse(new PendingRequestStore(memory.preferences(), 8)
                .isActive(request.nonce(), 1));
        assertFalse(memory.values.containsKey(request.nonce()));
        assertEquals(8, memory.values.get("boot_id"));
    }

    /** Verifies that a failed boot-marker write never reactivates persisted stale nonces. */
    @Test
    public void failedBootMarkerWriteDoesNotLoadPersistedNonce() {
        MemoryPreferences memory = new MemoryPreferences();
        memory.values.put("boot_id", 7);
        memory.values.put("old-nonce", 100000L);
        memory.commitSucceeds = false;
        PendingRequestStore store = new PendingRequestStore(memory.preferences(), 8);
        assertFalse(store.isActive("old-nonce", 1));
    }

    /** Verifies that an unknown boot clears persisted state but allows requests until recreation. */
    @Test
    public void unavailableBootCountDiscardsPersistedNonceAndOldBootId() {
        MemoryPreferences memory = new MemoryPreferences();
        memory.values.put("boot_id", 7);
        memory.values.put("old-nonce", 100000L);
        PendingRequestStore store = new PendingRequestStore(memory.preferences(), -1);
        assertFalse(store.isActive("old-nonce", 1000));
        assertTrue(memory.values.isEmpty());
        PendingRequestStore.Request request = store.create(1000);
        assertTrue(store.isActive(request.nonce(), 1001));
        assertFalse(new PendingRequestStore(memory.preferences(), -1)
                .isActive(request.nonce(), 1001));
    }

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
    public void newRequestSupersedesOldNonce() {
        PendingRequestStore store = new PendingRequestStore(new HashMap<>());
        PendingRequestStore.Request first = store.create(1000);
        PendingRequestStore.Request second = store.create(1001);
        assertFalse(store.isActive(first.nonce(), 1002));
        assertTrue(store.isActive(second.nonce(), 1002));
        store.complete(first.nonce());
        assertTrue(store.isActive(second.nonce(), 1002));
    }

    @Test
    public void completionInvalidatesNonce() {
        PendingRequestStore store = new PendingRequestStore(new HashMap<>());
        PendingRequestStore.Request request = store.create(1000);
        store.complete(request.nonce());
        assertFalse(store.isActive(request.nonce(), 1001));
    }

    private static final class MemoryPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private boolean commitSucceeds = true;

        /** Returns a map-backed proxy supporting only the preference reads and edits used here. */
        SharedPreferences preferences() {
            return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getAll": return new HashMap<>(values);
                            case "getInt": return values.getOrDefault(args[0], args[1]);
                            case "edit": return editor();
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }

        /** Stages integer and long writes, applying any clear before those writes on commit. */
        SharedPreferences.Editor editor() {
            Map<String, Object> changes = new HashMap<>();
            boolean[] clear = {false};
            return (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "clear": clear[0] = true; return proxy;
                            case "putInt": case "putLong":
                                changes.put((String) args[0], args[1]); return proxy;
                            case "commit":
                                if (!commitSucceeds) return false;
                                if (clear[0]) values.clear();
                                values.putAll(changes);
                                return true;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
