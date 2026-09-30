package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class TelegramSemanticResolverTest {
    private static final class SearchLike {
        int account;
        List<Object> a = new ArrayList<>();
        List<Object> b = new ArrayList<>();
        List<Object> c = new ArrayList<>();
        List<Object> d = new ArrayList<>();
        List<Object> e = new ArrayList<>();

        int renamedCount() { return 0; }
        Object renamedItem(int index) { return null; }
        void renamedSearch(int folder, String query) {}
    }

    private static final class MapCache implements TelegramSemanticResolver.Cache {
        final Map<String, String> values = new HashMap<>();
        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }

    @Test public void semanticDiscoveryIsCachedAndReusedWithoutRediscovery() {
        MapCache cache = new MapCache();
        AtomicInteger calls = new AtomicInteger();
        TelegramSemanticResolver first = new TelegramSemanticResolver(
                getClass().getClassLoader(), cache, target -> {
                    calls.incrementAndGet();
                    return List.of(SearchLike.class.getName());
                });

        assertEquals(SearchLike.class,
                first.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH));
        assertEquals(1, calls.get());

        TelegramSemanticResolver second = new TelegramSemanticResolver(
                getClass().getClassLoader(), cache, target -> {
                    throw new AssertionError("valid cache must bypass discovery");
                });
        assertEquals(SearchLike.class,
                second.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH));
    }

    @Test public void invalidCachedClassIsDiscardedBeforeSemanticDiscovery() {
        MapCache cache = new MapCache();
        cache.put("class.dialog_search", String.class.getName());
        TelegramSemanticResolver resolver = new TelegramSemanticResolver(
                getClass().getClassLoader(), cache,
                target -> List.of(SearchLike.class.getName()));

        assertEquals(SearchLike.class,
                resolver.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH));
        assertEquals(SearchLike.class.getName(), cache.get("class.dialog_search"));
    }

    @Test public void renamedMethodFallsBackToUniqueSignatureAndCachesDescriptor() {
        MapCache cache = new MapCache();
        TelegramSemanticResolver resolver = new TelegramSemanticResolver(
                getClass().getClassLoader(), cache, target -> List.of());

        Method count = resolver.resolveMethod(
                "search.count", SearchLike.class, int.class, new Class<?>[0], "h");
        assertEquals("renamedCount", count.getName());
        assertEquals("renamedCount", cache.get("method.search.count"));

        Method cached = resolver.resolveMethod(
                "search.count", SearchLike.class, int.class, new Class<?>[0], "doesNotExist");
        assertEquals(count, cached);
    }

    @Test public void ambiguousStructuralMethodFailsClosed() {
        class Ambiguous {
            int first() { return 0; }
            int second() { return 0; }
        }
        TelegramSemanticResolver resolver = new TelegramSemanticResolver(
                getClass().getClassLoader(), new MapCache(), target -> List.of());
        assertThrows(IllegalStateException.class, () -> resolver.resolveMethod(
                "ambiguous", Ambiguous.class, int.class, new Class<?>[0], "missing"));
    }
}
