package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class TelegramSearchHook {
    private static final Map<Object, int[]> POSITIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TelegramSearchHook() {}

    interface StatusReporter {
        void report(String status, String detail);
    }

    /**
     * Installs search count filtering, position remapping, and cache invalidation hooks.
     * Adds the post-refresh redraw hook when its optional reload target resolves.
     *
     * @throws ReflectiveOperationException if a required hook surface cannot be resolved
     */
    static void install(
            ClassLoader loader,
            TelegramSemanticResolver symbols,
            Supplier<HiddenConfig> config,
            BooleanSupplier revealed,
            Consumer<Object> trackAdapter, StatusReporter status) throws ReflectiveOperationException {
        Class<?> adapter = symbols.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH);
        Method count = symbols.resolveMethod(
                "search.count", adapter, int.class, new Class<?>[0], "h");
        Method item = symbols.resolveMethod(
                "search.item", adapter, null, new Class<?>[] {int.class}, "J");

        ModernHookBridge.hookMethod(count, new ModernHookBridge.MethodHook() {
            @Override
            protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) {
                try {
                    int sourceCount = ((Number) param.getResult()).intValue();
                    int[] positions = buildPositions(
                            param.thisObject, sourceCount, item, config, revealed, status);
                    POSITIONS.put(param.thisObject, positions);
                    trackAdapter.accept(param.thisObject);
                    param.setResult(positions.length);
                } catch (Throwable error) {
                    reportFailure(status, error);
                }
            }
        });

        hookPosition(item, 0, revealed, status);
        hookPosition(symbols.resolveMethod(
                "search.position.j", adapter, null, new Class<?>[] {int.class}, "j"),
                0, revealed, status);
        hookPosition(symbols.resolveMethod(
                "search.position.i", adapter, null, new Class<?>[] {int.class}, "i"),
                0, revealed, status);
        hookPosition(symbols.resolveMethodByArity(
                "search.position.v", adapter, 2, "v"), 1, revealed, status);

        ModernHookBridge.MethodHook invalidate = new ModernHookBridge.MethodHook() {
            @Override
            protected void beforeHookedMethod(ModernHookBridge.MethodHookParam param) {
                POSITIONS.remove(param.thisObject);
            }
        };
        ModernHookBridge.hookMethod(symbols.resolveMethod(
                "search.invalidate", adapter, null,
                new Class<?>[] {int.class, String.class}, "U"), invalidate);
        Class<?> view = symbols.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH_VIEW);
        ModernHookBridge.hookMethod(symbols.resolveMethodByArity(
                "search.view.invalidate", view, 0, "l"), invalidate);

        Method asyncReload = resolveOptionalReload(symbols, adapter, status);
        if (asyncReload != null) {
            ModernHookBridge.hookMethod(symbols.resolveMethodByArity(
                    "search.async.refresh", adapter, 0, "T"), new ModernHookBridge.MethodHook() {
                /** Redraws the adapter through the resolved target after async refresh. */
                @Override
                protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) {
                    invokeReload(asyncReload, param.thisObject);
                }
            });
        }
    }

    /**
     * The post-refresh redraw target has no semantic fingerprint, so it resolves only
     * from the validated alias or a unique zero-argument match. An unresolvable target
     * skips the optional redraw instead of invoking a raw obfuscated name; concealment
     * filtering never depends on it.
     */
    private static Method resolveOptionalReload(
            TelegramSemanticResolver symbols, Class<?> adapter, StatusReporter status) {
        try {
            return symbols.resolveMethodByArity("search.async.reload", adapter, 0, "l");
        } catch (IllegalStateException ambiguous) {
            status.report("installed", "async_reload_unavailable");
            ModernHookBridge.log(
                    "Royalty: search async reload unresolved; skipping optional redraw hook");
            return null;
        }
    }

    /**
     * Invokes the resolved redraw target, propagating its runtime exceptions and errors.
     *
     * @throws IllegalStateException if access fails or the target throws a checked exception
     */
    private static void invokeReload(Method reload, Object adapter) {
        try {
            reload.invoke(adapter);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("search async reload unavailable", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("search async reload failed", cause);
        }
    }

    /** Clears all cached visible-to-source position mappings. */
    static void invalidatePositions() { POSITIONS.clear(); }

    /** Remaps a visible position argument to its source index while chats are concealed. */
    private static void hookPosition(
            Method method,
            int argumentIndex,
            BooleanSupplier revealed,
            StatusReporter status) {
        ModernHookBridge.hookMethod(method, new ModernHookBridge.MethodHook() {
            @Override
            protected void beforeHookedMethod(ModernHookBridge.MethodHookParam param) {
                try {
                    if (revealed.getAsBoolean()) return;
                    int[] positions = POSITIONS.get(param.thisObject);
                    int visible = ((Number) param.args[argumentIndex]).intValue();
                    if (positions != null && visible >= 0 && visible < positions.length) {
                        param.args[argumentIndex] = positions[visible];
                    }
                } catch (Throwable error) {
                    reportFailure(status, error);
                }
            }
        });
    }

    private static int[] buildPositions(
            Object adapter,
            int sourceCount,
            Method item,
            Supplier<HiddenConfig> config,
            BooleanSupplier revealed,
            StatusReporter status) {
        int account = ModernHookBridge.getIntField(adapter, "r0");
        boolean[] degraded = {false};
        int[] positions = DialogFilter.visiblePositions(
                sourceCount,
                index -> invokeItem(item, adapter, index, degraded),
                row -> {
                    java.util.Optional<DialogKey> key =
                            TelegramObjectKey.fromSearchResult(account, row);
                    if (row != null && !key.isPresent()) degraded[0] = true;
                    return key;
                },
                config.get(),
                revealed.getAsBoolean());
        if (degraded[0]) {
            status.report("installed", "unknown_rows_visible");
        }
        return positions;
    }

    private static Object invokeItem(
            Method item, Object adapter, int index, boolean[] degraded) {
        try {
            return ModernHookBridge.invokeOriginalMethod(item, adapter, new Object[] {index});
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            degraded[0] = true;
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Method matched = null;
            for (Method method : current.getDeclaredMethods()) {
                if (name.equals(method.getName())
                        && method.getParameterTypes().length == parameterCount) {
                    if (matched != null) {
                        throw new NoSuchMethodException(current.getName() + "." + name
                                + " has ambiguous " + parameterCount + "-argument overloads");
                    }
                    matched = method;
                }
            }
            if (matched != null) {
                matched.setAccessible(true);
                return matched;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static void reportFailure(StatusReporter status, Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
        status.report("runtime_error", error.getClass().getSimpleName());
        ModernHookBridge.log("Royalty: search filtering failed open: " + error);
    }
}
