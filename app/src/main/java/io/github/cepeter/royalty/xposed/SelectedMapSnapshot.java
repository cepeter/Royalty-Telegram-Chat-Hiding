package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Validated structural traversal of the pinned z.f selected-dialog collection. */
final class SelectedMapSnapshot {
    private SelectedMapSnapshot() {}

    static final class Entry {
        final Object value;
        final long id;
        Entry(Object value, long id) { this.value = value; this.id = id; }
    }

    static final class Backing {
        private final Object target;
        private final Field keyField;
        private final Field valueField;
        private final Field countField;
        private final long[] originalKeys;
        private final Object[] originalValues;
        private final long[] savedKeys;
        private final Object[] savedValues;
        private final int originalCount;
        private final List<Entry> entries;

        Backing(Object target, Field keyField, Field valueField, Field countField,
                long[] keys, Object[] values, int count, List<Entry> entries) {
            this.target = target;
            this.keyField = keyField;
            this.valueField = valueField;
            this.countField = countField;
            originalKeys = keys;
            originalValues = values;
            savedKeys = keys.clone();
            savedValues = values.clone();
            originalCount = count;
            this.entries = entries;
        }

        List<Entry> entries() { return entries; }

        void restore() {
            try {
                // The clear/insert API may itself be broken; restore the validated
                // backing fields without calling it again.
                if (keyField.get(target) != originalKeys) keyField.set(target, originalKeys);
                if (valueField.get(target) != originalValues) valueField.set(target, originalValues);
                System.arraycopy(savedKeys, 0, originalKeys, 0, savedKeys.length);
                System.arraycopy(savedValues, 0, originalValues, 0, savedValues.length);
                countField.setInt(target, originalCount);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("selected-dialog map cannot be restored", error);
            }
        }
    }

    static List<Entry> read(Object selected, int account) {
        return capture(selected, account).entries();
    }

    static Backing capture(Object selected, int account) {
        if (selected == null || !"z.f".equals(selected.getClass().getName())) {
            throw new IllegalStateException("selected-dialog map is not the pinned z.f type");
        }
        List<Field> keyFields = new ArrayList<>();
        List<Field> valueFields = new ArrayList<>();
        List<Field> countFields = new ArrayList<>();
        for (Class<?> type = selected.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType() == long[].class) keyFields.add(field);
                else if (field.getType().isArray()
                        && !field.getType().getComponentType().isPrimitive()) valueFields.add(field);
                else if (field.getType() == int.class) countFields.add(field);
            }
        }
        if (keyFields.size() != 1 || valueFields.size() != 1) {
            throw new IllegalStateException("selected-dialog map arrays are ambiguous");
        }
        try {
            long[] keys = (long[]) keyFields.get(0).get(selected);
            Object[] values = (Object[]) valueFields.get(0).get(selected);
            if (keys == null || values == null) throw new IllegalStateException("null map storage");
            List<Backing> candidates = new ArrayList<>();
            for (Field countField : countFields) {
                int size = countField.getInt(selected);
                if (size < 0 || size > keys.length || size > values.length) continue;
                List<Entry> entries = new ArrayList<>(size);
                boolean valid = true;
                for (int index = 0; index < size; index++) {
                    Optional<DialogKey> key = TelegramObjectKey.fromDialog(account, values[index]);
                    if (!key.isPresent() || key.get().dialogId() != keys[index]) {
                        valid = false;
                        break;
                    }
                    entries.add(new Entry(values[index], keys[index]));
                }
                if (valid) candidates.add(new Backing(selected, keyFields.get(0),
                        valueFields.get(0), countField, keys, values, size, entries));
            }
            if (candidates.size() != 1) {
                throw new IllegalStateException("selected-dialog map size is ambiguous");
            }
            return candidates.get(0);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("selected-dialog map cannot be read", error);
        }
    }
}
