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
    private static final class Adapter {
        final RevealSession session;
        List<DialogKey> visible;
        int notifications;
        Adapter(RevealSession session) { this.session = session; }
        public void notifyDataSetChanged() {
            notifications++;
            visible = DialogFilter.filteredCopy(Arrays.asList(DialogKey.of(0, 7), DialogKey.of(0, 8)),
                    key -> key, HiddenConfig.fromStrings(java.util.Collections.singleton("0:7"), false),
                    session.revealed());
        }
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
        registry.refreshAll(value -> ((Adapter) value).notifyDataSetChanged());
        assertEquals(1, search.notifications);
        assertEquals(1, share.notifications);
        assertEquals(Arrays.asList(DialogKey.of(0, 8)), search.visible);
        session.toggle(100);
        registry.refreshAll(value -> ((Adapter) value).notifyDataSetChanged());
        assertEquals(Arrays.asList(DialogKey.of(0, 7), DialogKey.of(0, 8)), search.visible);
        session.toggle(200);
        registry.refreshAll(value -> ((Adapter) value).notifyDataSetChanged());
        assertEquals(Arrays.asList(DialogKey.of(0, 8)), share.visible);
        assertEquals(3, search.notifications);
    }
}
