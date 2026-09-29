package io.github.cepeter.royalty.xposed;

/**
 * Name-independent fixture derived from Telegram RecyclerView.Adapter at commit
 * 9552e5541e1274b9557c9832b204dbfcaf44b3dc (root declaration begins at line 6990).
 * Preserves ALL root Adapter no-argument return descriptors, Observable field,
 * Object superclass, abstractness, and parameterized void overload families.
 * Method names are deliberately renamed; these are not claimed APK aliases.
 * Android value types/generic holders in parameterized methods reduce to Object.
 */
abstract class PinnedRecyclerAdapterShape {
    abstract static class DataObservable extends android.database.Observable<Object> { }
    private final DataObservable observable = null; // metadata-only; no Android constructor in JVM
    private boolean stable;
    public abstract Object create(Object parent, int type);
    public abstract void bind(Object holder, int position);
    public void bind(Object holder, int position, java.util.List<Object> payloads) { }
    public final Object createBound(Object parent, int type) { return null; }
    public final void bindBound(Object holder, int position) { }
    public int viewType(int position) { return 0; }
    public void stableIds(boolean enabled) { stable = enabled; }
    public long itemId(int position) { return 0; }
    public abstract int count();
    public final boolean stableIds() { return stable; }
    public void recycled(Object holder) { }
    public boolean failedRecycle(Object holder) { return false; }
    public void attached(Object holder) { }
    public void detached(Object holder) { }
    public final boolean observers() { return false; }
    public void register(Object observer) { }
    public void unregister(Object observer) { }
    public void attachedToList(Object list) { }
    public void detachedFromList(Object list) { }
    public void q() { }
    public void changed(int position) { }
    public void changed(int position, Object payload) { }
    public void rangeChanged(int start, int count) { }
    public void rangeChanged(int start, int count, Object payload) { }
    public void inserted(int position) { }
    public void moved(int from, int to) { }
    public void rangeInserted(int start, int count) { }
    public void removed(int position) { }
    public void rangeRemoved(int start, int count) { }
}
