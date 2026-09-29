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

    private static final class MemoryPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failCommit;
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
                                return !failCommit;
                            default: throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
