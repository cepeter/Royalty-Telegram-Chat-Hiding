package io.github.cepeter.royalty.xposed;

import java.util.ArrayList;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Weakly tracks adapter instances observed by installed filter hooks. */
final class AdapterRefreshRegistry {
    private final WeakHashMap<Object, Boolean> adapters = new WeakHashMap<>();

    static void verifyRefreshMethod(Class<?> type) throws NoSuchMethodException {
        Method method = type.getMethod("notifyDataSetChanged");
        if (method.getReturnType() != void.class)
            throw new NoSuchMethodException(type.getName() + ".notifyDataSetChanged return type");
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
