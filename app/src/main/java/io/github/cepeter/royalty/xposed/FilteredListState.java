package io.github.cepeter.royalty.xposed;

import java.util.ArrayList;
import java.util.Collections;
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
        if (!current.equals(state.snapshot)) {
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
        List<Object> additions = new ArrayList<>(current);
        for (Object previous : snapshot) {
            int currentIndex = additions.indexOf(previous);
            if (currentIndex >= 0) {
                additions.remove(currentIndex);
            } else {
                raw.remove(previous);
            }
        }
        raw.addAll(additions);
        snapshot = new ArrayList<>(current);
    }
}
