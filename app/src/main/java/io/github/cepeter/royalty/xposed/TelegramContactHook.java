package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

    static void install(ClassLoader loader, TelegramSemanticResolver symbols,
            Supplier<HiddenConfig> config, BooleanSupplier revealed,
            Consumer<Object> trackAdapter, StatusReporter status) throws Exception {
        Class<?> group = symbols.resolveClass(TelegramSemanticResolver.Target.GROUP_ADAPTER);
        hookBefore(symbols.resolveMethodByArity(
                "contacts.group.count", group, 0, "h"), p -> {
            trackAdapter.accept(p.thisObject);
            applyGroup(p.thisObject, config.get(), revealed.getAsBoolean(), status);
        }, status);
        hookAfter(symbols.resolveMethod(
                "contacts.group.search", group, null, new Class<?>[] {String.class}, "L"), p -> {
            trackAdapter.accept(p.thisObject);
            applyGroup(p.thisObject, config.get(), revealed.getAsBoolean(), status);
        }, status);

        Class<?> search = symbols.resolveClass(TelegramSemanticResolver.Target.CONTACT_SEARCH);
        hookBefore(symbols.resolveMethodByArity(
                "contacts.search.count", search, 0, "h"), p -> {
            if (search.isInstance(p.thisObject)) {
                trackAdapter.accept(p.thisObject);
                applySearch(p.thisObject, config.get(), revealed.getAsBoolean(), status);
            }
        }, status);
        hookAfter(symbols.resolveMethod(
                "contacts.search.query", search, null, new Class<?>[] {String.class}, "G"), p -> {
            if (search.isInstance(p.thisObject)) {
                trackAdapter.accept(p.thisObject);
                applySearch(p.thisObject, config.get(), revealed.getAsBoolean(), status);
            }
        }, status);

        installSections(loader, symbols, config, revealed, trackAdapter, status);
    }

    private static void installSections(ClassLoader loader, TelegramSemanticResolver symbols,
            Supplier<HiddenConfig> config, BooleanSupplier revealed,
            Consumer<Object> trackAdapter, StatusReporter status) throws Exception {
        Class<?> concrete = symbols.resolveClass(TelegramSemanticResolver.Target.CONTACT_LIST);
        Method refresh = symbols.resolveMethodByArity(
                "contacts.sections.refresh", concrete, 0, "l");
        Method count = symbols.resolveMethod(
                "contacts.sections.count", concrete, int.class, new Class<?>[] {int.class}, "M");
        Method item = symbols.resolveMethod(
                "contacts.sections.item", concrete, null,
                new Class<?>[] {int.class, int.class}, "O");
        ModernHookBridge.hookMethod(count, new ModernHookBridge.MethodHook() {
            @Override protected void afterHookedMethod(ModernHookBridge.MethodHookParam p) {
                if (!concrete.isInstance(p.thisObject)) return;
                safely(status, () -> {
                    trackAdapter.accept(p.thisObject);
                    buildSection(p, item, config.get(), revealed.getAsBoolean());
                });
            }
        });
        for (Method method : sectionPositionMethods(concrete))
            hookSectionPosition(method, concrete, revealed, status);
        ModernHookBridge.hookMethod(refresh, new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                SECTIONS.remove(p.thisObject);
            }
        });
    }

    static void invalidateSections() { SECTIONS.clear(); }

    static List<Method> sectionPositionMethods(Class<?> concrete) {
        List<Method> methods = new ArrayList<>();
        for (String name : new String[] {"O", "N", "P", "V", "W"}) {
            for (Class<?> current = concrete; current != null && current != Object.class;
                    current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    int modifiers = method.getModifiers();
                    if (name.equals(method.getName()) && method.getParameterCount() >= 2
                            && !Modifier.isAbstract(modifiers) && !Modifier.isStatic(modifiers)
                            && !method.isSynthetic() && !method.isBridge()) {
                        methods.add(method);
                    }
                }
            }
        }
        return methods;
    }

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

    private static void hookSectionPosition(Method method, Class<?> concrete,
            BooleanSupplier revealed, StatusReporter status) {
        ModernHookBridge.hookMethod(method, new ModernHookBridge.MethodHook() {
            @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam p) {
                if (!concrete.isInstance(p.thisObject) || revealed.getAsBoolean()) return;
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
