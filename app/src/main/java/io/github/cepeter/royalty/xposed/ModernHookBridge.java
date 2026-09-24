package io.github.cepeter.royalty.xposed;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ModernHookBridge {
    private static XposedModule module;

    private ModernHookBridge() {}

    static synchronized void attach(XposedModule attachedModule) {
        if (module != null && module != attachedModule) {
            throw new IllegalStateException("Modern Xposed framework already attached");
        }
        module = attachedModule;
    }

    static XposedInterface.HookHandle hookMethod(Executable executable, MethodHook callback) {
        executable.setAccessible(true);
        return requireModule()
                .hook(executable)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        MethodHookParam param = new MethodHookParam(
                                chain.getExecutable(),
                                chain.getThisObject(),
                                chain.getArgs().toArray());
                        callback.beforeHookedMethod(param);
                        if (!param.returnEarly) {
                            try {
                                param.result = chain.proceed(param.args);
                            } catch (Throwable error) {
                                param.throwable = error;
                            }
                        }
                        callback.afterHookedMethod(param);
                        if (param.throwable != null) {
                            throw param.throwable;
                        }
                        return param.result;
                    }
                });
    }

    static List<XposedInterface.HookHandle> hookAllMethods(
            Class<?> type, String name, MethodHook callback) {
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName())) {
                handles.add(hookMethod(method, callback));
            }
        }
        return handles;
    }

    static XposedInterface.HookHandle findAndHookMethod(
            String className, ClassLoader loader, String methodName, Object... signatureAndCallback) {
        return findAndHookMethod(findClass(className, loader), methodName, signatureAndCallback);
    }

    static XposedInterface.HookHandle findAndHookMethod(
            Class<?> type, String methodName, Object... signatureAndCallback) {
        if (signatureAndCallback.length == 0
                || !(signatureAndCallback[signatureAndCallback.length - 1] instanceof MethodHook)) {
            throw new IllegalArgumentException("method hook callback is required");
        }
        MethodHook callback = (MethodHook) signatureAndCallback[signatureAndCallback.length - 1];
        Class<?>[] parameterTypes = new Class<?>[signatureAndCallback.length - 1];
        for (int index = 0; index < parameterTypes.length; index++) {
            if (!(signatureAndCallback[index] instanceof Class<?>)) {
                throw new IllegalArgumentException("method parameter type is required");
            }
            parameterTypes[index] = (Class<?>) signatureAndCallback[index];
        }
        return hookMethod(findMethod(type, methodName, parameterTypes), callback);
    }

    static Class<?> findClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException error) {
            throw new ClassLookupException(className, error);
        }
    }

    static Object getObjectField(Object target, String name) {
        try {
            return findField(target.getClass(), name).get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("cannot read field " + name, error);
        }
    }

    static int getIntField(Object target, String name) {
        return ((Number) getObjectField(target, name)).intValue();
    }

    static long getLongField(Object target, String name) {
        return ((Number) getObjectField(target, name)).longValue();
    }

    static int getStaticIntField(Class<?> type, String name) {
        try {
            return findField(type, name).getInt(null);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("cannot read static field " + name, error);
        }
    }

    static void setObjectField(Object target, String name, Object value) {
        try {
            findField(target.getClass(), name).set(target, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("cannot write field " + name, error);
        }
    }

    static Object callMethod(Object target, String name, Object... args) {
        return invokeReflectively(findCompatibleMethod(target.getClass(), name, false, args), target, args);
    }

    static Object callStaticMethod(Class<?> type, String name, Object... args) {
        return invokeReflectively(findCompatibleMethod(type, name, true, args), null, args);
    }

    static Object invokeOriginalMethod(Method method, Object target, Object[] args) throws Throwable {
        try {
            return requireModule()
                    .getInvoker(method)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    static void log(String message) {
        requireModule().log(android.util.Log.ERROR, "Royalty", message);
    }

    private static XposedModule requireModule() {
        XposedModule current = module;
        if (current == null) {
            throw new IllegalStateException("Modern Xposed framework is not attached");
        }
        return current;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Telegram moves methods between adapter base classes across releases.
            }
        }
        throw new IllegalArgumentException(type.getName() + "." + name + Arrays.toString(parameters));
    }

    private static Method findCompatibleMethod(
            Class<?> type, String name, boolean requireStatic, Object[] args) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!name.equals(method.getName())
                        || Modifier.isStatic(method.getModifiers()) != requireStatic
                        || !parametersAccept(method.getParameterTypes(), args)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalArgumentException(type.getName() + "." + name + " with " + args.length + " args");
    }

    private static boolean parametersAccept(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameters.length; index++) {
            Object argument = args[index];
            if (argument == null) {
                if (parameters[index].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> expected = boxed(parameters[index]);
            if (!expected.isAssignableFrom(argument.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return Void.class;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Telegram moves fields between adapter base classes across releases.
            }
        }
        throw new IllegalArgumentException(type.getName() + "." + name);
    }

    private static Object invokeReflectively(Method method, Object target, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("method invocation failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("method invocation failed", error);
        }
    }

    abstract static class MethodHook {
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    }

    static final class MethodHookParam {
        public final Executable method;
        public final Object thisObject;
        public final Object[] args;
        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        MethodHookParam(Executable method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        Object getResult() {
            return result;
        }

        void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }
    }

    static final class ClassLookupException extends RuntimeException {
        ClassLookupException(String className, Throwable cause) {
            super(className, cause);
        }
    }
}
