package io.github.cepeter.royalty.xposed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

final class FilteredListState {
    private final List<Object> raw;
    private List<?> applied;
    private List<?> snapshot;

    private FilteredListState(List<?> source) {
        raw = new ArrayList<>(source);
    }

    static FilteredListState capture(List<?> current, FilteredListState state) {
        if (state == null || current != state.applied) {
            return new FilteredListState(current);
        }
        if (!sameIdentityContents(current, state.snapshot)) {
            state.reconcile(current);
        }
        return state;
    }

    List<Object> raw() {
        return Collections.unmodifiableList(raw);
    }

    void markApplied(List<?> value) {
        applied = value;
        snapshot = new ArrayList<>(value);
    }

    private void reconcile(List<?> current) {
        // Telegram rows are identity-owned; value equality can merge distinct rows.
        IdentityHashMap<Object, Integer> removals = identityCounts(snapshot);
        consumeAll(removals, current);
        retainWithout(removals);

        IdentityHashMap<Object, Integer> additions = identityCounts(current);
        consumeAll(additions, snapshot);
        appendFrom(current, additions);
        snapshot = new ArrayList<>(current);
    }

    private static boolean sameIdentityContents(List<?> left, List<?> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index)) return false;
        }
        return true;
    }

    private static IdentityHashMap<Object, Integer> identityCounts(List<?> values) {
        IdentityHashMap<Object, Integer> counts = new IdentityHashMap<>();
        for (Object value : values) {
            counts.put(value, counts.getOrDefault(value, 0) + 1);
        }
        return counts;
    }

    private static void consumeAll(
            IdentityHashMap<Object, Integer> counts, List<?> values) {
        for (Object value : values) {
            consume(counts, value);
        }
    }

    private static boolean consume(IdentityHashMap<Object, Integer> counts, Object value) {
        Integer count = counts.get(value);
        if (count == null) return false;
        if (count == 1) counts.remove(value);
        else counts.put(value, count - 1);
        return true;
    }

    private void retainWithout(IdentityHashMap<Object, Integer> removals) {
        List<Object> retained = new ArrayList<>(raw.size());
        for (Object value : raw) {
            if (!consume(removals, value)) retained.add(value);
        }
        raw.clear();
        raw.addAll(retained);
    }

    private void appendFrom(List<?> current, IdentityHashMap<Object, Integer> additions) {
        for (Object value : current) {
            if (consume(additions, value)) raw.add(value);
        }
    }
}
