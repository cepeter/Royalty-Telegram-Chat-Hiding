package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

final class TelegramShareHook {
    private static final Map<Object, FilteredListState> MAIN = weakMap();
    private static final Map<Object, FilteredListState> SEARCH = weakMap();
    private static final Map<Object, FilteredListState> HELPER = weakMap();
    private static final Map<Object, FilteredListState> RECENT = weakMap();
    private static final Map<Object, Boolean> LAST_REVEAL = weakMap();

    private TelegramShareHook() {}

    interface StatusReporter { void report(String status, String detail); }

    static void install(ClassLoader loader, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, StatusReporter status) throws Exception {
        Class<?> main = Class.forName("org.telegram.ui.Components.oq0", false, loader);
        Class<?> search = Class.forName("org.telegram.ui.Components.sq0", false, loader);
        ModernHookBridge.hookMethod(main.getDeclaredMethod("E"), new ModernHookBridge.MethodHook() {
            @Override protected void afterHookedMethod(ModernHookBridge.MethodHookParam p) {
                safely(status, () -> applyMain(p.thisObject, config, revealed, status));
            }
        });
        ModernHookBridge.hookMethod(main.getDeclaredMethod("h"), new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                safely(status, () -> applyMain(p.thisObject, config, revealed, status));
            }
        });
        ModernHookBridge.hookMethod(search.getDeclaredMethod("E", String.class), new ModernHookBridge.MethodHook() {
            @Override protected void afterHookedMethod(ModernHookBridge.MethodHookParam p) {
                safely(status, () -> applySearch(p.thisObject, config, revealed, status));
            }
        });
        ModernHookBridge.hookMethod(search.getDeclaredMethod("h"), new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                safely(status, () -> applySearch(p.thisObject, config, revealed, status));
            }
        });
    }

    private static void applyMain(Object adapter, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, StatusReporter status) {
        Object outer = ModernHookBridge.getObjectField(adapter, "f");
        int account = ModernHookBridge.getIntField(outer, "currentAccount");
        HiddenConfig hidden = config.get();
        boolean reveal = revealed.getAsBoolean();
        FilteredListState state = capture(adapter, "d", MAIN);
        List<Object> visible = filter(state.raw(),
                row -> TelegramObjectKey.fromDialog(account, row),
                hidden, reveal, status);
        apply(adapter, "d", state, visible);
        Object oldMap = ModernHookBridge.getObjectField(adapter, "e");
        try {
            replaceMapContents(oldMap, visible, state.raw(), account);
        } catch (RuntimeException error) {
            apply(adapter, "d", state, new ArrayList<>(state.raw()));
            throw error;
        }
        guardSelection(outer, state.raw(), account, hidden, reveal);
    }

    private static void applySearch(Object adapter, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, StatusReporter status) {
        Object outer = ModernHookBridge.getObjectField(adapter, "J");
        int account = ModernHookBridge.getIntField(outer, "currentAccount");
        HiddenConfig hidden = config.get();
        boolean reveal = revealed.getAsBoolean();
        filterField(adapter, "d", SEARCH,
                row -> TelegramObjectKey.fromShareSearch(account, row), hidden, reveal, status);
        Object helper = ModernHookBridge.getObjectField(adapter, "e");
        filterField(helper, "d", HELPER,
                row -> TelegramObjectKey.fromSearchResult(account, row), hidden, reveal, status);
        filterField(outer, "D0", RECENT,
                row -> TelegramObjectKey.fromRecent(account, row), hidden, reveal, status);
    }

    private static void filterField(Object owner, String field, Map<Object, FilteredListState> states,
            Function<Object, java.util.Optional<DialogKey>> extractor,
            HiddenConfig config, boolean reveal, StatusReporter status) {
        FilteredListState state = capture(owner, field, states);
        apply(owner, field, state, filter(state.raw(), extractor, config, reveal, status));
    }

    private static FilteredListState capture(
            Object owner, String field, Map<Object, FilteredListState> states) {
        List<?> current = (List<?>) ModernHookBridge.getObjectField(owner, field);
        FilteredListState state = FilteredListState.capture(current, states.get(owner));
        states.put(owner, state);
        return state;
    }

    private static void apply(
            Object owner, String field, FilteredListState state, List<Object> value) {
        ModernHookBridge.setObjectField(owner, field, value);
        state.markApplied(value);
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

    private static void replaceMapContents(
            Object target, List<Object> dialogs, List<Object> rollbackDialogs, int account) {
        List<Object> verifiedDialogs = new ArrayList<>();
        List<Long> verifiedIds = new ArrayList<>();
        collectMapEntries(dialogs, account, verifiedDialogs, verifiedIds);

        List<Object> rollbackVerifiedDialogs = new ArrayList<>();
        List<Long> rollbackVerifiedIds = new ArrayList<>();
        collectMapEntries(
                rollbackDialogs, account, rollbackVerifiedDialogs, rollbackVerifiedIds);

        try {
            writeMapContents(target, verifiedDialogs, verifiedIds);
        } catch (RuntimeException error) {
            try {
                writeMapContents(target, rollbackVerifiedDialogs, rollbackVerifiedIds);
            } catch (RuntimeException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    private static void collectMapEntries(
            List<Object> dialogs, int account, List<Object> verifiedDialogs, List<Long> verifiedIds) {
        for (Object dialog : dialogs) {
            java.util.Optional<DialogKey> key = TelegramObjectKey.fromDialog(account, dialog);
            if (key.isPresent()) {
                verifiedDialogs.add(dialog);
                verifiedIds.add(key.get().dialogId());
            }
        }
    }

    private static void writeMapContents(
            Object target, List<Object> dialogs, List<Long> ids) {
        ModernHookBridge.callMethod(target, "b");
        for (int index = 0; index < dialogs.size(); index++) {
            ModernHookBridge.callMethod(target, "k", dialogs.get(index), ids.get(index));
        }
    }

    private static void guardSelection(Object outer, List<Object> dialogs, int account,
            HiddenConfig config, boolean reveal) {
        Boolean previous = LAST_REVEAL.put(outer, reveal);
        if (previous == null || !previous || reveal) return;
        Object selected = ModernHookBridge.getObjectField(outer, "T");
        List<Object> originalSelected = new ArrayList<>();
        List<Object> retained = new ArrayList<>();
        for (Object dialog : dialogs) {
            java.util.Optional<DialogKey> key = TelegramObjectKey.fromDialog(account, dialog);
            if (!key.isPresent()) continue;
            long id = key.get().dialogId();
            if (((Number) ModernHookBridge.callMethod(selected, "h", id)).intValue() >= 0) {
                originalSelected.add(dialog);
                if (!config.isHidden(key.get())) {
                    retained.add(dialog);
                }
            }
        }
        replaceMapContents(selected, retained, originalSelected, account);
    }

    private static void safely(StatusReporter status, Runnable action) {
        try {
            action.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (RuntimeException error) {
            status.report("runtime_error", error.getClass().getSimpleName());
            ModernHookBridge.log("Royalty: share filtering failed open: " + error);
        }
    }

    private static <K, V> Map<K, V> weakMap() {
        return Collections.synchronizedMap(new WeakHashMap<>());
    }
}
