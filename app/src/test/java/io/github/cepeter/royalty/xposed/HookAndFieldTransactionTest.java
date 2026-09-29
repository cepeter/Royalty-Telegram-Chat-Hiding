package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class HookAndFieldTransactionTest {
    private static final class Handle implements XposedInterface.HookHandle {
        final String name;
        final List<String> removed;
        Handle(String name, List<String> removed) { this.name = name; this.removed = removed; }
        @Override public Executable getExecutable() { return null; }
        @Override public void unhook() { removed.add(name); }
    }

    @Test
    public void failedSurfaceUnhooksEveryPartialRegistrationInReverseOrder() {
        List<String> removed = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> ModernHookBridge.installAtomically(() -> {
            ModernHookBridge.trackHandle(new Handle("first", removed));
            ModernHookBridge.trackHandle(new Handle("second", removed));
            throw new IllegalStateException("third registration failed");
        }));
        assertEquals(Arrays.asList("second", "first"), removed);
    }

    @Test
    public void successfulSurfaceKeepsAllRegistrations() throws Throwable {
        List<String> removed = new ArrayList<>();
        ModernHookBridge.installAtomically(() ->
                ModernHookBridge.trackHandle(new Handle("first", removed)));
        assertTrue(removed.isEmpty());
    }

    @Test public void realHookMethodChainPassesThroughAfterFailedInstallEvenWhenUnhookFails() throws Throwable {
        java.lang.reflect.Field moduleField = ModernHookBridge.class.getDeclaredField("module");
        moduleField.setAccessible(true); Object previousModule = moduleField.get(null);
        List<XposedInterface.Hooker> hooks = new ArrayList<>();
        java.lang.reflect.Method target = String.class.getMethod("substring", int.class);
        XposedInterface framework = (XposedInterface) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {XposedInterface.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getApiVersion")) return 101;
                    if (method.getName().equals("hook")) return new XposedInterface.HookBuilder() {
                        public XposedInterface.HookBuilder setPriority(int p) { return this; }
                        public XposedInterface.HookBuilder setExceptionMode(XposedInterface.ExceptionMode mode) { return this; }
                        public XposedInterface.HookHandle intercept(XposedInterface.Hooker hooker) {
                            hooks.add(hooker);
                            return new XposedInterface.HookHandle() {
                                public Executable getExecutable() { return target; }
                                public void unhook() { throw new IllegalStateException("framework unhook failure"); }
                            };
                        }
                    };
                    throw new UnsupportedOperationException(method.getName());
                });
        io.github.libxposed.api.XposedModule fake = new io.github.libxposed.api.XposedModule() {};
        fake.attachFramework(framework); moduleField.set(null, fake);
        int[] callbackCalls = {0}, proceeds = {0};
        XposedInterface.Chain chain = (XposedInterface.Chain) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getExecutable": return target;
                        case "getThisObject": return "original";
                        case "getArgs": return Arrays.asList(2);
                        case "proceed": proceeds[0]++; assertArrayEquals(new Object[] {2}, (Object[]) args[0]); return "iginal";
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        try {
            assertThrows(IllegalStateException.class, () -> ModernHookBridge.installAtomically(() -> {
                ModernHookBridge.hookMethod(target, new ModernHookBridge.MethodHook() {
                    @Override protected void beforeHookedMethod(ModernHookBridge.MethodHookParam param) {
                        callbackCalls[0]++; param.setResult("filtered");
                    }
                    @Override protected void afterHookedMethod(ModernHookBridge.MethodHookParam param) { callbackCalls[0]++; }
                });
                // Even interception during installation must not execute a partial callback.
                assertEquals("iginal", hooks.get(0).intercept(chain));
                throw new IllegalStateException("later registration failed");
            }));
            assertEquals("iginal", hooks.get(0).intercept(chain));
            assertEquals(0, callbackCalls[0]); assertEquals(2, proceeds[0]);
        } finally { moduleField.set(null, previousModule); }
    }

    private static final class Fields {
        List<String> items = new ArrayList<>(Arrays.asList("original"));
        List<String> names = new ArrayList<>(Arrays.asList("name"));
    }

    @Test
    public void secondWriteFailureRestoresFirstFieldAndOriginalReferences() {
        Fields owner = new Fields();
        List<String> originalItems = owner.items;
        List<String> originalNames = owner.names;
        assertThrows(IllegalStateException.class, () -> FieldWriteTransaction.writePair(
                owner, "items", new ArrayList<>(Arrays.asList("filtered")),
                "names", new ArrayList<>(Arrays.asList("filtered-name")),
                (target, field, value) -> {
                    if ("names".equals(field)) throw new IllegalStateException("write failed");
                    ModernHookBridge.setObjectField(target, field, value);
                }));
        assertSame(originalItems, owner.items);
        assertSame(originalNames, owner.names);
    }
}
