package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class TelegramContactHook {
    private static final Map<Object, Map<String, FilteredListState>> LISTS = weakMap();
    private static final Map<Object, Map<Integer, int[]>> SECTIONS = weakMap();

    private TelegramContactHook() {}
    interface StatusReporter { void report(String status, String detail); }

    static void install(ClassLoader loader, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, Consumer<Object> trackAdapter, StatusReporter status) throws Exception {
        Class<?> group = Class.forName("org.telegram.ui.q70", false, loader);
        AdapterRefreshRegistry.verifyRefreshMethod(group);
        hookBefore(ModernHookBridge.findMethod(adapter, "h"), p -> {
            trackAdapter.accept(p.thisObject);
            applyGroup(p.thisObject, config.get(), revealed.getAsBoolean(), status);
        }, status);
        hookAfter(ModernHookBridge.findMethod(adapter, "L", String.class), p -> {
            trackAdapter.accept(p.thisObject);
            applyGroup(p.thisObject, config.get(), revealed.getAsBoolean(), status);
        }, status);

        Class<?> search = Class.forName("we.g1", false, loader);
        AdapterRefreshRegistry.verifyRefreshMethod(Class.forName("org.telegram.ui.mt", false, loader));
        hookBefore(ModernHookBridge.findMethod(adapter, "h"), p -> {
            if (isClass(p.thisObject, "org.telegram.ui.mt")) {
                trackAdapter.accept(p.thisObject);
                applySearch(p.thisObject, config.get(), revealed.getAsBoolean(), status);
            }
        }, status);
        hookAfter(ModernHookBridge.findMethod(adapter, "G", String.class), p -> {
            if (isClass(p.thisObject, "org.telegram.ui.mt")) {
                trackAdapter.accept(p.thisObject);
                applySearch(p.thisObject, config.get(), revealed.getAsBoolean(), status);
            }
        }, status);

        installSections(loader, config, revealed, trackAdapter, status);
    }

    private static void installSections(ClassLoader loader, Supplier<HiddenConfig> config,
            BooleanSupplier revealed, Consumer<Object> trackAdapter, StatusReporter status) throws Exception {
        Class<?> base = Class.forName("we.d", false, loader);
        Class<?> concrete = Class.forName("org.telegram.ui.nt", false, loader);
        AdapterRefreshRegistry.verifyRefreshMethod(concrete);
        Method refresh = ModernHookBridge.findMethod(adapter, "l");
        Method count = ModernHookBridge.findMethod(adapter, "M", int.class);
        Method item = ModernHookBridge.findMethod(adapter, "O", int.class, int.class);
        ModernHookBridge.hookMethod(count, new ModernHookBridge.MethodHook() {
            @Override protected void afterHookedMethod(ModernHookBridge.MethodHookParam p) {
                if (!isClass(p.thisObject, "org.telegram.ui.nt")) return;
                safely(status, () -> {
                    trackAdapter.accept(p.thisObject);
                    buildSection(p, item, config.get(), revealed.getAsBoolean());
                });
            }
        });
        for (String name : new String[] {"O", "N", "P", "V", "W"}) {
            for (Method method : base.getDeclaredMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().length >= 2)
                    hookSectionPosition(method, revealed, status);
            }
        }
        ModernHookBridge.hookMethod(refresh, new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                SECTIONS.remove(p.thisObject);
            }
        });
    }

    static void invalidateSections() { SECTIONS.clear(); }

    private static void buildSection(ModernHookBridge.MethodHookParam p, Method item,
            HiddenConfig config, boolean reveal) {
        int section = ((Number) p.args[0]).intValue();
        int count = ((Number) p.getResult()).intValue();
        int account = ModernHookBridge.getIntField(p.thisObject, "r");
        int[] positions = DialogFilter.visiblePositions(count,
                row -> invokeItem(item, p.thisObject, section, row),
                value -> TelegramObjectKey.fromPickerResult(account, value), config, reveal);
        SECTIONS.computeIfAbsent(p.thisObject, ignored -> new HashMap<>()).put(section, positions);
        p.setResult(positions.length);
    }

    private static void hookSectionPosition(Method method, BooleanSupplier revealed,
            StatusReporter status) {
        ModernHookBridge.hookMethod(method, new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                if (!isClass(p.thisObject, "org.telegram.ui.nt") || revealed.getAsBoolean()) return;
                safely(status, () -> {
                    int section = ((Number) p.args[0]).intValue();
                    int row = ((Number) p.args[1]).intValue();
                    Map<Integer, int[]> sections = SECTIONS.get(p.thisObject);
                    int[] positions = sections == null ? null : sections.get(section);
                    if (positions != null && row >= 0 && row < positions.length)
                        p.args[1] = positions[row];
                });
            }
        });
    }

    private static void applyGroup(Object adapter, HiddenConfig config, boolean reveal,
            StatusReporter status) {
        Object outer = ModernHookBridge.getObjectField(adapter, "H");
        int account = ModernHookBridge.getIntField(outer, "currentAccount");
        filterPaired(adapter, "d", "e", account, config, reveal, status);
        filterField(adapter, "r", value -> TelegramObjectKey.fromPickerResult(account, value),
                config, reveal, status);
        Object helper = ModernHookBridge.getObjectField(adapter, "f");
        filterHelper(helper, account, config, reveal, status);
    }

    private static void applySearch(Object adapter, HiddenConfig config, boolean reveal,
            StatusReporter status) {
        Object outer = ModernHookBridge.getObjectField(adapter, "J");
        int account = ModernHookBridge.getIntField(outer, "currentAccount");
        filterPaired(adapter, "d", "e", account, config, reveal, status);
        filterField(adapter, "G", value -> TelegramObjectKey.fromPickerResult(account, value),
                config, reveal, status);
        filterHelper(ModernHookBridge.getObjectField(adapter, "f"),
                account, config, reveal, status);
    }

    private static void filterHelper(Object helper, int account, HiddenConfig config,
            boolean reveal, StatusReporter status) {
        for (String field : new String[] {"d", "e", "g", "j", "k", "l"})
            filterField(helper, field,
                    value -> TelegramObjectKey.fromPickerResult(account, value),
                    config, reveal, status);
    }

    private static void filterPaired(Object owner, String itemsField, String namesField,
            int account, HiddenConfig config, boolean reveal, StatusReporter status) {
        FilteredListState items = capture(owner, itemsField);
        FilteredListState names = capture(owner, namesField);
        DialogFilter.PairedCopy<Object, Object> result = DialogFilter.filteredPairedCopy(
                items.raw(), names.raw(),
                value -> TelegramObjectKey.fromPickerResult(account, value).orElse(null),
                config, reveal);
        ArrayList<Object> itemCopy = new ArrayList<>(result.items());
        ArrayList<Object> nameCopy = new ArrayList<>(result.metadata());
        FieldWriteTransaction.writePair(owner, itemsField, itemCopy, namesField, nameCopy);
        items.markApplied(itemCopy);
        names.markApplied(nameCopy);
    }

    private static void filterField(Object owner, String field,
            Function<Object, java.util.Optional<DialogKey>> extractor,
            HiddenConfig config, boolean reveal, StatusReporter status) {
        FilteredListState state = capture(owner, field);
        boolean[] unknown = {false};
        List<Object> result = DialogFilter.filteredCopy(state.raw(), value -> {
            java.util.Optional<DialogKey> key = extractor.apply(value);
            if (value != null && !key.isPresent()) unknown[0] = true;
            return key.orElse(null);
        }, config, reveal);
        if (unknown[0]) status.report("installed", "unknown_rows_visible");
        apply(owner, field, state, result);
    }

    private static FilteredListState capture(Object owner, String field) {
        List<?> current = (List<?>) ModernHookBridge.getObjectField(owner, field);
        synchronized (LISTS) {
            Map<String, FilteredListState> fields = LISTS.get(owner);
            if (fields == null) {
                fields = new HashMap<>();
                LISTS.put(owner, fields);
            }
            FilteredListState state = FilteredListState.capture(current, fields.get(field));
            fields.put(field, state);
            return state;
        }
    }

    private static void apply(
            Object owner, String field, FilteredListState state, List<?> value) {
        ArrayList<Object> copy = new ArrayList<>(value);
        ModernHookBridge.setObjectField(owner, field, copy);
        state.markApplied(copy);
    }

    private static Object invokeItem(Method method, Object owner, int section, int row) {
        try {
            return ModernHookBridge.invokeOriginalMethod(method, owner, new Object[] {section, row});
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void hookBefore(Method method, HookAction action, StatusReporter status) {
        ModernHookBridge.hookMethod(method, new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                safely(status, () -> action.run(p));
            }
        });
    }

    private static void hookAfter(Method method, HookAction action, StatusReporter status) {
        ModernHookBridge.hookMethod(method, new ModernHookBridge.MethodHook() {
            @Override protected void afterHookedMethod(ModernHookBridge.MethodHookParam p) {
                safely(status, () -> action.run(p));
            }
        });
    }

    private static void safely(StatusReporter status, Runnable action) {
        try {
            action.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (RuntimeException error) {
            status.report("runtime_error", error.getClass().getSimpleName());
            ModernHookBridge.log("Royalty: contact filtering failed open: " + error);
        }
    }

    private static boolean isClass(Object value, String name) {
        return value != null && name.equals(value.getClass().getName());
    }

    private static <K, V> Map<K, V> weakMap() {
        return Collections.synchronizedMap(new WeakHashMap<>());
    }

    private interface HookAction { void run(ModernHookBridge.MethodHookParam param); }
}
