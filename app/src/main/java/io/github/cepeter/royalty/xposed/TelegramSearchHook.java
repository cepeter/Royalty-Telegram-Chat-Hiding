package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogFilter;
import io.github.cepeter.royalty.core.DialogKey;
import io.github.cepeter.royalty.core.HiddenConfig;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class TelegramSearchHook {
    private static final Map<Object, int[]> POSITIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TelegramSearchHook() {}

    interface StatusReporter {
        void report(String status, String detail);
    }

    static void install(
            ClassLoader loader,
            Supplier<HiddenConfig> config,
            BooleanSupplier revealed,
            StatusReporter status) throws ReflectiveOperationException {
        Class<?> adapter = Class.forName("we.b0", false, loader);
        Method count = adapter.getDeclaredMethod("h");
        Method item = adapter.getDeclaredMethod("J", int.class);

        ModernHookBridge.hookMethod(count, new ModernHookBridge.MethodHook() {
            @Override
            protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) {
                try {
                    int sourceCount = ((Number) param.getResult()).intValue();
                    int[] positions = buildPositions(
                            param.thisObject, sourceCount, item, config, revealed, status);
                    POSITIONS.put(param.thisObject, positions);
                    param.setResult(positions.length);
                } catch (Throwable error) {
                    reportFailure(status, error);
                }
            }
        });

        hookPosition(adapter.getDeclaredMethod("J", int.class), 0, revealed, status);
        hookPosition(adapter.getDeclaredMethod("j", int.class), 0, revealed, status);
        hookPosition(adapter.getDeclaredMethod("i", int.class), 0, revealed, status);
        hookPosition(findMethod(adapter, "v", 2), 1, revealed, status);

        ModernHookBridge.MethodHook invalidate = new ModernHookBridge.MethodHook() {
            @Override
            protected void beforeHookedMethod(ModernHookBridge.MethodHookParam param) {
                POSITIONS.remove(param.thisObject);
            }
        };
        ModernHookBridge.hookMethod(adapter.getDeclaredMethod("U", int.class, String.class), invalidate);
        Class<?> view = Class.forName("org.telegram.ui.Components.eo0", false, loader);
        if (ModernHookBridge.hookAllMethods(view, "l", invalidate).isEmpty()) {
            throw new NoSuchMethodException(view.getName() + ".l");
        }

        ModernHookBridge.hookMethod(adapter.getDeclaredMethod("T"), new ModernHookBridge.MethodHook() {
            @Override
            protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) {
                ModernHookBridge.callMethod(param.thisObject, "l");
            }
        });
    }

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
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName())
                    && method.getParameterTypes().length == parameterCount) {
                return method;
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
