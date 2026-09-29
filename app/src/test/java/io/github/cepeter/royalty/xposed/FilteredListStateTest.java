package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public final class FilteredListStateTest {
    private static final class ValueEqualRow {
        final int id;
        ValueEqualRow(int id) { this.id = id; }
        @Override public boolean equals(Object other) {
            return other instanceof ValueEqualRow && ((ValueEqualRow) other).id == id;
        }
        @Override public int hashCode() { return id; }
    }
    @Test
    public void inPlaceChangesMergeIntoFullBaselineWithoutDroppingHiddenRows() {
        ArrayList<Object> original = new ArrayList<>(Arrays.asList("visible", "hidden"));
        FilteredListState state = FilteredListState.capture(original, null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList("visible"));
        state.markApplied(applied);

        applied.add("new");
        assertSame(state, FilteredListState.capture(applied, state));
        assertEquals(Arrays.asList("visible", "new", "hidden"), state.raw());

        applied.remove("visible");
        assertSame(state, FilteredListState.capture(applied, state));
        assertEquals(Arrays.asList("new", "hidden"), state.raw());
    }

    @Test
    public void reconcileUsesObjectIdentityRatherThanValueEquality() {
        ValueEqualRow first = new ValueEqualRow(7);
        ValueEqualRow replacement = new ValueEqualRow(7);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList(first));
        FilteredListState state = FilteredListState.capture(applied, null);
        state.markApplied(applied);

        applied.set(0, replacement);
        FilteredListState.capture(applied, state);

        assertSame(replacement, state.raw().get(0));
    }

    @Test
    public void replacementReferenceStartsANewBaseline() {
        FilteredListState state = FilteredListState.capture(
                new ArrayList<>(Arrays.asList("old", "hidden")), null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList("old"));
        state.markApplied(applied);

        ArrayList<Object> replacement = new ArrayList<>(Arrays.asList("replacement"));
        FilteredListState replaced = FilteredListState.capture(replacement, state);

        assertNotSame(state, replaced);
        assertEquals(Arrays.asList("replacement"), replaced.raw());
    }

    @Test
    public void insertsAtFrontAndMiddleWithoutDisplacingConcealedRows() {
        Object a = new Object(), hidden = new Object(), b = new Object();
        Object front = new Object(), middle = new Object();
        FilteredListState state = FilteredListState.capture(new ArrayList<>(Arrays.asList(a, hidden, b)), null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList(a, b));
        state.markApplied(applied);
        applied.add(0, front);
        applied.add(2, middle);
        FilteredListState.capture(applied, state);
        assertEquals(Arrays.asList(front, a, middle, hidden, b), state.raw());
    }

    @Test
    public void reordersVisibleRowsAndAnchorsHiddenBeforeNextSurvivor() {
        Object a = new Object(), hidden = new Object(), b = new Object(), c = new Object();
        FilteredListState state = FilteredListState.capture(new ArrayList<>(Arrays.asList(a, hidden, b, c)), null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList(a, b, c));
        state.markApplied(applied);
        applied.clear();
        applied.addAll(Arrays.asList(c, b, a));
        FilteredListState.capture(applied, state);
        assertEquals(Arrays.asList(c, hidden, b, a), state.raw());
    }

    @Test
    public void removalReplacementAndRevealRetainOrphanedHiddenInOldOrder() {
        Object a = new Object(), h1 = new Object(), b = new Object(), h2 = new Object();
        Object replacement = new Object();
        FilteredListState state = FilteredListState.capture(new ArrayList<>(Arrays.asList(a, h1, b, h2)), null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList(a, b));
        state.markApplied(applied);
        applied.clear();
        applied.add(replacement);
        FilteredListState.capture(applied, state);
        assertEquals(Arrays.asList(replacement, h1, h2), state.raw());
        assertEquals(Arrays.asList(replacement, h1, h2), new ArrayList<>(state.raw()));
    }

    @Test
    public void duplicateIdentityReintroducedFromHiddenDoesNotMultiply() {
        Object repeated = new Object(), visible = new Object();
        FilteredListState state = FilteredListState.capture(
                new ArrayList<>(Arrays.asList(repeated, visible, repeated)), null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList(repeated, visible));
        state.markApplied(applied);
        applied.add(0, repeated);
        FilteredListState.capture(applied, state);
        assertEquals(3, state.raw().size());
        assertSame(repeated, state.raw().get(0));
        assertSame(repeated, state.raw().get(1));
        assertSame(visible, state.raw().get(2));
    }

    @Test
    public void nullOccurrenceAndRepeatedCaptureDoNotDuplicateRows() {
        Object visible = new Object();
        FilteredListState state = FilteredListState.capture(new ArrayList<>(Arrays.asList(null, visible)), null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList(visible));
        state.markApplied(applied);
        applied.add(0, null);
        FilteredListState.capture(applied, state);
        FilteredListState.capture(applied, state);
        assertEquals(2, state.raw().size());
        assertNull(state.raw().get(0));
        assertSame(visible, state.raw().get(1));
    }
}
