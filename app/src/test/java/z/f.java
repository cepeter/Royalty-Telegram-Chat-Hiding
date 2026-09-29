package z;

import java.util.Arrays;

public final class f {
    private long[] keys = new long[8];
    private Object[] values = new Object[8];
    private int size;
    public boolean failNextInsert;

    public void b() {
        Arrays.fill(keys, 0);
        Arrays.fill(values, null);
        size = 0;
    }

    public void k(Object value, long key) {
        if (failNextInsert) {
            failNextInsert = false;
            throw new IllegalStateException("insertion failed");
        }
        keys[size] = key;
        values[size++] = value;
    }

    public int h(long key) {
        for (int index = 0; index < size; index++) if (keys[index] == key) return index;
        return -1;
    }

    public int size() { return size; }
    public Object valueAt(int index) { return values[index]; }
}
