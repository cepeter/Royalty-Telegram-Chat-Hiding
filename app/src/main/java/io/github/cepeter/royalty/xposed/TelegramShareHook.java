package io.github.cepeter.royalty.xposed;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

final class TelegramShareHook {
    private static final Map<Object, ListState> MAIN = weakMap();
    private static final Map<Object, ListState> SEARCH = weakMap();
    private static final Map<Object, ListState> HELPER = weakMap();
    private static final Map<Object, ListState> RECENT = weakMap();
    private static final Map<Object, Boolean> LAST_REVEAL = weakMap();

    private TelegramShareHook() {}

    interface StatusReporter { void report(String status, String detail); }

    static void install(ClassLoader loader, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, StatusReporter status) throws Exception {
        Class<?> main = Class.forName("org.telegram.ui.Components.oq0", false, loader);
        Class<?> search = Class.forName("org.telegram.ui.Components.sq0", false, loader);
        XposedBridge.hookMethod(main.getDeclaredMethod("E"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                safely(status, () -> applyMain(p.thisObject, config, revealed, status));
            }
        });
        XposedBridge.hookMethod(main.getDeclaredMethod("h"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                safely(status, () -> applyMain(p.thisObject, config, revealed, status));
            }
        });
        XposedBridge.hookMethod(search.getDeclaredMethod("E", String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                safely(status, () -> applySearch(p.thisObject, config, revealed, status));
            }
        });
        XposedBridge.hookMethod(search.getDeclaredMethod("h"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                safely(status, () -> applySearch(p.thisObject, config, revealed, status));
            }
        });
    }

    private static void applyMain(Object adapter, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, StatusReporter status) {
        Object outer = XposedHelpers.getObjectField(adapter, "f");
        int account = XposedHelpers.getIntField(outer, "currentAccount");
        HiddenConfig hidden = config.get();
        boolean reveal = revealed.getAsBoolean();
        ListState state = capture(adapter, "d", MAIN);
        List<Object> visible = filter(state.raw,
                row -> TelegramObjectKey.fromDialog(account, row),
                hidden, reveal, status);
        apply(adapter, "d", state, visible);
        Object oldMap = XposedHelpers.getObjectField(adapter, "e");
        XposedHelpers.setObjectField(adapter, "e", dialogMap(oldMap, visible));
        guardSelection(outer, state.raw, account, hidden, reveal);
    }

    private static void applySearch(Object adapter, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, StatusReporter status) {
        Object outer = XposedHelpers.getObjectField(adapter, "J");
        int account = XposedHelpers.getIntField(outer, "currentAccount");
        HiddenConfig hidden = config.get();
        boolean reveal = revealed.getAsBoolean();
        filterField(adapter, "d", SEARCH,
                row -> TelegramObjectKey.fromShareSearch(account, row), hidden, reveal, status);
        Object helper = XposedHelpers.getObjectField(adapter, "e");
        filterField(helper, "d", HELPER,
                row -> TelegramObjectKey.fromSearchResult(account, row), hidden, reveal, status);
        filterField(outer, "D0", RECENT,
                row -> TelegramObjectKey.fromRecent(account, row), hidden, reveal, status);
    }

    private static void filterField(Object owner, String field, Map<Object, ListState> states,
            Function<Object, java.util.Optional<DialogKey>> extractor,
            HiddenConfig config, boolean reveal, StatusReporter status) {
        ListState state = capture(owner, field, states);
        apply(owner, field, state, filter(state.raw, extractor, config, reveal, status));
    }

    private static ListState capture(Object owner, String field, Map<Object, ListState> states) {
        List<?> current = (List<?>) XposedHelpers.getObjectField(owner, field);
        ListState state = states.get(owner);
        if (state == null || current != state.applied || !current.equals(state.snapshot)) {
            state = new ListState(new ArrayList<>(current));
            states.put(owner, state);
        }
        return state;
    }

    private static void apply(Object owner, String field, ListState state, List<Object> value) {
        XposedHelpers.setObjectField(owner, field, value);
        state.applied = value;
        state.snapshot = new ArrayList<>(value);
    }

    private static List<Object> filter(List<Object> source,
            Function<Object, java.util.Optional<DialogKey>> extractor,
            HiddenConfig config, boolean reveal, StatusReporter status) {
        boolean[] unknown = {false};
        List<Object> result = DialogFilter.filteredCopy(source, row -> {
            java.util.Optional<DialogKey> key = extractor.apply(row);
            if (row != null && !key.isPresent()) unknown[0] = true;
            return key.orElse(null);
        }, config, reveal);
        if (unknown[0]) status.report("installed", "unknown_rows_visible");
        return result;
    }

    private static Object dialogMap(Object template, List<Object> dialogs) {
        try {
            Constructor<?> constructor = template.getClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            Object result = constructor.newInstance();
            for (Object dialog : dialogs) {
                java.util.Optional<DialogKey> key = TelegramObjectKey.fromDialog(0, dialog);
                if (key.isPresent()) {
                    XposedHelpers.callMethod(result, "k", dialog, key.get().dialogId());
                }
            }
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("cannot rebuild share dialog map", error);
        }
    }

    private static void guardSelection(Object outer, List<Object> dialogs, int account,
            HiddenConfig config, boolean reveal) {
        Boolean previous = LAST_REVEAL.put(outer, reveal);
        if (previous == null || !previous || reveal) return;
        Object selected = XposedHelpers.getObjectField(outer, "T");
        Object replacement = dialogMap(selected, Collections.emptyList());
        for (Object dialog : dialogs) {
            java.util.Optional<DialogKey> key = TelegramObjectKey.fromDialog(account, dialog);
            if (!key.isPresent() || config.isHidden(key.get())) continue;
            long id = key.get().dialogId();
            if (((Number) XposedHelpers.callMethod(selected, "h", id)).intValue() >= 0) {
                XposedHelpers.callMethod(replacement, "k", dialog, id);
            }
        }
        XposedHelpers.setObjectField(outer, "T", replacement);
    }

    private static void safely(StatusReporter status, Runnable action) {
        try {
            action.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (RuntimeException error) {
            status.report("runtime_error", error.getClass().getSimpleName());
            XposedBridge.log("Royalty: share filtering failed open: " + error);
        }
    }

    private static <K, V> Map<K, V> weakMap() {
        return Collections.synchronizedMap(new WeakHashMap<>());
    }

    private static final class ListState {
        final List<Object> raw;
        List<?> applied;
        List<?> snapshot;

        ListState(List<Object> raw) {
            this.raw = raw;
        }
    }
}
