package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;
import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import io.github.cepeter.royalty.core.RevealSession;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class AdapterRefreshRegistryTest {
    private static final class Adapter extends PinnedRecyclerAdapterShape {
        public Object create(Object parent, int type) { return null; }
        public void bind(Object holder, int position) { }
        public int count() { return 0; }
        final RevealSession session;
        List<DialogKey> visible;
        int notifications;
        Adapter(RevealSession session) { this.session = session; }
        public void q() {
            notifications++;
            visible = DialogFilter.filteredCopy(Arrays.asList(DialogKey.of(0, 7), DialogKey.of(0, 8)),
                    key -> key, HiddenConfig.fromStrings(java.util.Collections.singleton("0:7"), false),
                    session.revealed());
        }
    }

    private abstract static class Ambiguous {
        private final PinnedRecyclerAdapterShape.DataObservable observable = null;
        public void one() {} public void two() {}
    }
    private abstract static class StaticOnly {
        private final PinnedRecyclerAdapterShape.DataObservable observable = null;
        public static void one() {}
    }
    @Test public void rejectsAmbiguityAndStaticDecoyAndCachesVirtualMethod() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> AdapterRefreshRegistry.resolve(Ambiguous.class));
        assertThrows(NoSuchMethodException.class, () -> AdapterRefreshRegistry.resolve(StaticOnly.class));
        java.lang.reflect.Method resolved = AdapterRefreshRegistry.resolve(Adapter.class);
        assertSame(resolved, AdapterRefreshRegistry.resolve(Adapter.class));
        assertSame(PinnedRecyclerAdapterShape.class, resolved.getDeclaringClass());
        Adapter adapter = new Adapter(new RevealSession());
        AdapterRefreshRegistry.refresh(adapter); assertEquals(1, adapter.notifications);
    }

    @Test public void installedAdapterMustExposePublicNoArgRefresh() throws Exception {
        AdapterRefreshRegistry.verifyRefreshMethod(Adapter.class);
        try { AdapterRefreshRegistry.verifyRefreshMethod(Object.class); fail(); }
        catch (NoSuchMethodException expected) { }
    }

    @Test public void refreshRebindsEveryObservedAdapterOnRevealAndConceal() {
        RevealSession session = new RevealSession();
        AdapterRefreshRegistry registry = new AdapterRefreshRegistry();
        Adapter search = new Adapter(session);
        Adapter share = new Adapter(session);
        registry.track(search);
        registry.track(share);
        registry.track(search);
        registry.refreshAll(value -> {
            try { AdapterRefreshRegistry.refresh(value); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        assertEquals(1, search.notifications);
        assertEquals(1, share.notifications);
        assertEquals(Arrays.asList(DialogKey.of(0, 8)), search.visible);
        session.toggle(100);
        registry.refreshAll(value -> {
            try { AdapterRefreshRegistry.refresh(value); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        assertEquals(Arrays.asList(DialogKey.of(0, 7), DialogKey.of(0, 8)), search.visible);
        session.toggle(200);
        registry.refreshAll(value -> {
            try { AdapterRefreshRegistry.refresh(value); } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        assertEquals(Arrays.asList(DialogKey.of(0, 8)), share.visible);
        assertEquals(3, search.notifications);
    }
}
