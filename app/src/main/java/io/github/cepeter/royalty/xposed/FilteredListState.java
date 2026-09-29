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
        // Identify the old visible occurrences in the full baseline, leaving concealed
        // occurrences available for reveal. All matching is by reference, including null.
        boolean[] oldVisible = new boolean[raw.size()];
        int visibleIndex = 0;
        for (int index = 0; index < raw.size() && visibleIndex < snapshot.size(); index++) {
            if (raw.get(index) == snapshot.get(visibleIndex)) {
                oldVisible[index] = true;
                visibleIndex++;
            }
        }
        if (visibleIndex != snapshot.size()) {
            throw new IllegalStateException("applied rows no longer match the baseline");
        }

        IdentityHashMap<Object, Integer> oldCounts = identityCounts(snapshot);
        IdentityHashMap<Object, Integer> currentCounts = identityCounts(current);
        IdentityHashMap<Object, Integer> restored = new IdentityHashMap<>();
        for (Object value : currentCounts.keySet()) {
            int excess = currentCounts.get(value) - oldCounts.getOrDefault(value, 0);
            if (excess > 0) restored.put(value, excess);
        }

        List<List<Object>> anchored = new ArrayList<>(snapshot.size());
        for (int index = 0; index < snapshot.size(); index++) anchored.add(new ArrayList<>());
        List<Object> orphaned = new ArrayList<>();
        boolean[] survives = new boolean[snapshot.size()];
        IdentityHashMap<Object, Integer> remaining = identityCounts(current);
        for (int i = 0; i < snapshot.size(); i++) survives[i] = consume(remaining, snapshot.get(i));
        int nextVisible = -1;
        int[] nextAnchors = new int[raw.size()];
        int ordinal = snapshot.size();
        for (int index = raw.size() - 1; index >= 0; index--) {
            if (oldVisible[index]) { --ordinal; if (survives[ordinal]) nextVisible = ordinal; }
            nextAnchors[index] = nextVisible;
        }
        for (int index = 0; index < raw.size(); index++) {
            if (oldVisible[index] || consume(restored, raw.get(index))) continue;
            int anchor = nextAnchors[index];
            if (anchor < 0) orphaned.add(raw.get(index));
            else anchored.get(anchor).add(raw.get(index));
        }

        IdentityHashMap<Object, List<Integer>> oldPositions = new IdentityHashMap<>();
        for (int index = 0; index < snapshot.size(); index++) {
            oldPositions.computeIfAbsent(snapshot.get(index), ignored -> new ArrayList<>()).add(index);
        }
        IdentityHashMap<Object, Integer> used = new IdentityHashMap<>();
        List<Object> reordered = new ArrayList<>(raw.size() + current.size());
        boolean[] anchorEmitted = new boolean[snapshot.size()];
        for (Object value : current) {
            int occurrence = used.getOrDefault(value, 0);
            used.put(value, occurrence + 1);
            List<Integer> positions = oldPositions.get(value);
            if (positions != null && occurrence < positions.size()) {
                int position = positions.get(occurrence);
                reordered.addAll(anchored.get(position));
                anchorEmitted[position] = true;
            }
            reordered.add(value);
        }
        for (int index = 0; index < anchored.size(); index++) {
            if (!anchorEmitted[index]) reordered.addAll(anchored.get(index));
        }
        reordered.addAll(orphaned);
        raw.clear();
        raw.addAll(reordered);
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

    private static boolean consume(IdentityHashMap<Object, Integer> counts, Object value) {
        Integer count = counts.get(value);
        if (count == null) return false;
        if (count == 1) counts.remove(value);
        else counts.put(value, count - 1);
        return true;
    }

}
