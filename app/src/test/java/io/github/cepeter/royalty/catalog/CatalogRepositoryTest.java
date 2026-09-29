package io.github.cepeter.royalty.catalog;

import static org.junit.Assert.*;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class CatalogRepositoryTest {
    @Test public void storesCompleteOwnerInventoryWithActiveEmptyAccount() {
        MemoryPreferences memory = new MemoryPreferences();
        CatalogRepository repository = new CatalogRepository(memory.preferences());
        CatalogBatch batch = new CatalogBatch("owners");
        assertTrue(batch.begin("owners", new int[] {0, 1}, new long[] {101, 202},
                new String[] {"Alice", "Bob"}, true, "session"));
        assertTrue(batch.account("owners", 0, 101, new long[] {42}, new String[] {"Chat"}));
        assertTrue(batch.account("owners", 1, 202, new long[0], new String[0]));
        assertTrue(batch.status("owners", new String[0], new String[0], new String[0], "session", 100));
        assertTrue(repository.replaceSnapshot(batch.complete("owners").get()));
        assertEquals(101, repository.loadCatalog().get(0).ownerId());
        assertTrue(repository.loadInventory().complete());
        assertEquals(202, repository.loadInventory().owner(1).id());
    }

    @Test public void checkedCommitPreservesLastGoodOnFailureAndReplacesItOnSuccess() {
        MemoryPreferences memory = new MemoryPreferences();
        CatalogRepository repository = new CatalogRepository(memory.preferences());
        CatalogBatch first = new CatalogBatch("one");
        assertTrue(first.begin("one", new int[] {0}, "session-1"));
        assertTrue(first.account("one", 0, new long[] {10}, new String[] {"Alice"}));
        assertTrue(first.status("one", new String[] {"search"}, new String[] {"installed"},
                new String[] {""}, "session-1", 1000));
        assertTrue(repository.replaceSnapshot(first.complete("one").get()));
        assertEquals(1, repository.loadCatalog().size());
        assertEquals(1000, repository.observedAtMillis());

        CatalogBatch second = new CatalogBatch("two");
        assertTrue(second.begin("two", new int[0], "session-2"));
        assertTrue(second.status("two", new String[0], new String[0], new String[0], "session-2", 2000));
        memory.failCommit = true;
        assertFalse(repository.replaceSnapshot(second.complete("two").get()));
        // Android may update the in-memory map before a failed disk commit.
        assertTrue(memory.values.containsKey("process_session"));
        assertEquals("session-2", memory.values.get("process_session"));
        repository = new CatalogRepository(memory.preferences());
        assertEquals(1, repository.loadCatalog().size());
        assertEquals("installed", repository.loadHookStatuses().get("search"));
        assertEquals(1000, repository.observedAtMillis());
        assertEquals("session-1", repository.processSession());
        memory.failCommit = false;
        CatalogBatch third = new CatalogBatch("three");
        assertTrue(third.begin("three", new int[0], "session-3"));
        assertTrue(third.status("three", new String[0], new String[0], new String[0], "session-3", 3000));
        assertTrue(repository.replaceSnapshot(third.complete("three").get()));
        assertTrue(repository.loadCatalog().isEmpty());
        assertTrue(repository.loadHookStatuses().isEmpty());
    }

    @Test public void incompleteSuccessRepairsFailedCompletePreferenceImage() {
        MemoryPreferences memory = new MemoryPreferences();
        CatalogRepository repository = new CatalogRepository(memory.preferences());
        assertTrue(repository.replaceSnapshot(ownerSnapshot("a", 101, true)));
        memory.failCommit = true;
        assertFalse(repository.replaceSnapshot(ownerSnapshot("b", 202, true)));
        memory.failCommit = false;
        assertTrue(repository.replaceSnapshot(ownerSnapshot("c", 0, false)));
        assertEquals(101L, repository.loadInventory().owner(0).id());
        // A new preference identity represents a fresh process reading the persisted image.
        memory.instance = null;
        repository = new CatalogRepository(memory.preferences());
        assertEquals(101L, repository.loadInventory().owner(0).id());
        assertEquals(101L, repository.loadCatalog().get(0).ownerId());
        assertEquals("0:101", repository.loadCatalog().get(0).key().toString());
        assertFalse(memory.values.containsKey("dialog.0:202"));
        assertFalse(repository.loadInventory().complete());
    }
    @Test public void throwingCompleteFailureIsNotPersistedByLaterIncompleteSuccess() {
        MemoryPreferences memory = new MemoryPreferences();
        CatalogRepository repository = new CatalogRepository(memory.preferences());
        assertTrue(repository.replaceSnapshot(ownerSnapshot("a", 101, true)));
        memory.throwCommit = true;
        assertFalse(repository.replaceSnapshot(ownerSnapshot("b", 202, true)));
        memory.throwCommit = false;
        assertTrue(repository.replaceSnapshot(ownerSnapshot("c", 0, false)));
        memory.instance = null;
        repository = new CatalogRepository(memory.preferences());
        assertEquals(101L, repository.loadInventory().owner(0).id());
        assertEquals("0:101", repository.loadCatalog().get(0).key().toString());
        assertFalse(memory.values.containsKey("dialog.0:202"));
    }

    private static CatalogBatch.Snapshot ownerSnapshot(String nonce, long owner, boolean complete) {
        CatalogBatch batch = new CatalogBatch(nonce);
        assertTrue(batch.begin(nonce, complete ? new int[] {0} : new int[0],
                complete ? new long[] {owner} : new long[0],
                complete ? new String[] {"Owner"} : new String[0], complete, nonce));
        if (complete) assertTrue(batch.account(nonce, 0, owner, new long[] {owner}, new String[] {"Chat"}));
        assertTrue(batch.status(nonce, new String[0], new String[0], new String[0], nonce, 100));
        return batch.complete(nonce).get();
    }

    private static final class MemoryPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failCommit;
        private boolean throwCommit;
        private SharedPreferences instance;
        SharedPreferences preferences() {
            if (instance != null) return instance;
            instance = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
                        if ("getAll".equals(method.getName())) return new HashMap<>(values);
                        if ("getLong".equals(method.getName())) return values.getOrDefault(args[0], args[1]);
                        if ("getString".equals(method.getName())) return values.getOrDefault(args[0], args[1]);
                        if ("edit".equals(method.getName())) return editor();
                        throw new UnsupportedOperationException(method.getName());
                    });
            return instance;
        }
        SharedPreferences.Editor editor() {
            Map<String, Object> changes = new HashMap<>();
            boolean[] clear = {false};
            return (SharedPreferences.Editor) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "clear": clear[0] = true; return proxy;
                            case "remove": changes.put((String) args[0], null); return proxy;
                            case "putString": case "putLong": changes.put((String) args[0], args[1]); return proxy;
                            case "commit":
                                if (clear[0]) values.clear();
                                for (Map.Entry<String, Object> entry : changes.entrySet()) {
                                    if (entry.getValue() == null) values.remove(entry.getKey());
                                    else values.put(entry.getKey(), entry.getValue());
                                }
                                if (throwCommit) throw new IllegalStateException("commit failed after memory mutation");
                                return !failCommit;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
