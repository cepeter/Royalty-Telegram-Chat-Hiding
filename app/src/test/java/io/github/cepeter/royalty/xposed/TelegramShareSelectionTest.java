package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;

import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import z.f;

public final class TelegramShareSelectionTest {
    private static final class Dialog {
        final long id;
        Dialog(long id) { this.id = id; }
    }
    private static final class Outer { final f T = new f(); }

    private static HiddenConfig hidden(String... ids) {
        return HiddenConfig.fromStrings(new HashSet<>(Arrays.asList(ids)), false);
    }

    private static void guard(Outer outer, List<Object> mainRows,
            int account, HiddenConfig config, boolean reveal) throws Exception {
        Method method = TelegramShareHook.class.getDeclaredMethod("guardSelection",
                Object.class, List.class, int.class, HiddenConfig.class, boolean.class);
        method.setAccessible(true);
        try {
            method.invoke(null, outer, mainRows, account, config, reveal);
        } catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException) throw (RuntimeException) error.getCause();
            throw error;
        }
    }

    private static boolean lastSuccessfulReveal(Outer outer) throws Exception {
        java.lang.reflect.Field markerMap = TelegramShareHook.class.getDeclaredField("LAST_REVEAL");
        markerMap.setAccessible(true);
        Object marker = ((Map<?, ?>) markerMap.get(null)).get(outer);
        java.lang.reflect.Field reveal = marker.getClass().getDeclaredField("reveal");
        reveal.setAccessible(true);
        return reveal.getBoolean(marker);
    }

    @Test
    public void searchOnlyVisibleSelectionSurvivesCleanup() throws Exception {
        Outer outer = new Outer();
        Dialog mainHidden = new Dialog(1), searchVisible = new Dialog(2);
        outer.T.k(mainHidden, 1);
        outer.T.k(searchVisible, 2);
        guard(outer, Arrays.asList(mainHidden), 0, hidden("0:1"), true);
        guard(outer, Arrays.asList(mainHidden), 0, hidden("0:1"), false);
        assertEquals(1, outer.T.size());
        assertSame(searchVisible, outer.T.valueAt(0));
    }

    @Test
    public void configurationChangeWhileConcealedRemovesNewlyHiddenSelectionOnlyForOwner() throws Exception {
        Outer outer = new Outer();
        Dialog first = new Dialog(1), second = new Dialog(2);
        outer.T.k(first, 1);
        outer.T.k(second, 2);
        guard(outer, Collections.emptyList(), 0, HiddenConfig.empty(), false);
        guard(outer, Collections.emptyList(), 0, hidden("0:1", "1:2"), false);
        assertEquals(1, outer.T.size());
        assertSame(second, outer.T.valueAt(0));
    }

    @Test
    public void failedMutationRestoresSelectionAndRetryRemovesHiddenRecipient() throws Exception {
        Outer outer = new Outer();
        Dialog hidden = new Dialog(1), visible = new Dialog(2);
        outer.T.k(hidden, 1);
        outer.T.k(visible, 2);
        outer.T.failNextInsert = true;
        guard(outer, Collections.emptyList(), 0, HiddenConfig.empty(), true);
        assertThrows(IllegalStateException.class,
                () -> guard(outer, Collections.emptyList(), 0, hidden("0:1"), false));
        assertEquals(2, outer.T.size());
        assertSame(hidden, outer.T.valueAt(0));
        assertSame(visible, outer.T.valueAt(1));
        assertTrue(lastSuccessfulReveal(outer));
        guard(outer, Collections.emptyList(), 0, hidden("0:1"), false);
        assertEquals(1, outer.T.size());
        assertSame(visible, outer.T.valueAt(0));
        assertFalse(lastSuccessfulReveal(outer));
    }

    @Test
    public void persistentInsertFailurePreservesOriginalSelectionAndMapIdentity() throws Exception {
        Outer outer = new Outer();
        f selected = outer.T;
        Dialog hidden = new Dialog(1), visible = new Dialog(2);
        selected.k(hidden, 1);
        selected.k(visible, 2);
        guard(outer, Collections.emptyList(), 0, HiddenConfig.empty(), true);
        selected.alwaysFailInsert = true;

        assertThrows(IllegalStateException.class,
                () -> guard(outer, Collections.emptyList(), 0, hidden("0:1"), false));
        assertSame(selected, outer.T);
        assertEquals(2, selected.size());
        assertSame(hidden, selected.valueAt(0));
        assertSame(visible, selected.valueAt(1));
        assertTrue(lastSuccessfulReveal(outer));

        selected.alwaysFailInsert = false;
        guard(outer, Collections.emptyList(), 0, hidden("0:1"), false);
        assertEquals(1, selected.size());
        assertSame(visible, selected.valueAt(0));
    }
}
