package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;

public final class FilteredListStateTest {
    @Test
    public void inPlaceChangesMergeIntoFullBaselineWithoutDroppingHiddenRows() {
        ArrayList<Object> original = new ArrayList<>(Arrays.asList("visible", "hidden"));
        FilteredListState state = FilteredListState.capture(original, null);
        ArrayList<Object> applied = new ArrayList<>(Arrays.asList("visible"));
        state.markApplied(applied);

        applied.add("new");
        assertSame(state, FilteredListState.capture(applied, state));
        assertEquals(Arrays.asList("visible", "hidden", "new"), state.raw());

        applied.remove("visible");
        assertSame(state, FilteredListState.capture(applied, state));
        assertEquals(Arrays.asList("hidden", "new"), state.raw());
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
}
