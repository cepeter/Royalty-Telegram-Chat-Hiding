package io.github.cepeter.royalty.xposed;

import android.database.Observable;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Weak instance tracking and cached, name-independent pinned RecyclerView contract. */
final class AdapterRefreshRegistry {
    private final WeakHashMap<Object, Boolean> adapters = new WeakHashMap<>();
    private static final Map<Class<?>, Method> REFRESH = new HashMap<>();

    static void verifyRefreshMethod(Class<?> type) throws NoSuchMethodException { resolve(type); }

    static synchronized Method resolve(Class<?> type) throws NoSuchMethodException {
        Method cached = REFRESH.get(type);
        if (cached != null) return cached;
        Method resolved = null;
        for (Class<?> root = type; root != null && root != Object.class; root = root.getSuperclass()) {
            // Pinned root Adapter extends Object, is abstract, and owns one private final
            // AdapterDataObservable, a direct subclass of stable Android Observable.
            int observables = 0;
            for (Field field : root.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers) && Modifier.isPrivate(modifiers)
                        && Modifier.isFinal(modifiers) && field.getType().getSuperclass() == Observable.class)
                    observables++;
            }
            if (observables == 0) continue;
            if (observables != 1 || root.getSuperclass() != Object.class || !Modifier.isAbstract(root.getModifiers()))
                throw new NoSuchMethodException("ambiguous RecyclerView root: " + type.getName());
            for (Method method : root.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)
                        && method.getParameterCount() == 0 && method.getReturnType() == void.class) {
                    if (resolved != null || Modifier.isAbstract(modifiers) || method.isSynthetic() || method.isBridge())
                        throw new NoSuchMethodException("ambiguous RecyclerView refresh: " + type.getName());
                    resolved = method;
                }
            }
        }
        if (resolved == null) throw new NoSuchMethodException("missing RecyclerView refresh descriptor: " + type.getName());
        resolved.setAccessible(true);
        REFRESH.put(type, resolved);
        return resolved;
    }

    static void refresh(Object adapter) throws ReflectiveOperationException {
        // Method.invoke preserves virtual dispatch for host overrides of the root method.
        resolve(adapter.getClass()).invoke(adapter);
    }

    synchronized void track(Object adapter) {
        if (adapter != null) adapters.put(adapter, Boolean.TRUE);
    }
    void refreshAll(Consumer<Object> refresh) {
        ArrayList<Object> snapshot;
        synchronized (this) { snapshot = new ArrayList<>(adapters.keySet()); }
        for (Object adapter : snapshot) refresh.accept(adapter);
    }
}
