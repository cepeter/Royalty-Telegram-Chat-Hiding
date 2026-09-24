package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public final class DialogFilterTest {
    private static final class Item {
        final long id;

        Item(long id) {
            this.id = id;
        }
    }

    @Test
    public void returnsFilteredCopyWithoutMutatingTelegramList() {
        List<Item> telegramList = new ArrayList<>(Arrays.asList(new Item(1), new Item(2), new Item(3)));
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        List<Item> filtered = DialogFilter.filteredCopy(
                telegramList, item -> DialogKey.of(0, item.id), config, false);

        assertNotSame(telegramList, filtered);
        assertEquals(Arrays.asList(1L, 3L), Arrays.asList(filtered.get(0).id, filtered.get(1).id));
        assertEquals(3, telegramList.size());
    }

    @Test
    public void revealReturnsUnfilteredCopy() {
        List<Item> telegramList = Arrays.asList(new Item(1), new Item(2));
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), true);

        List<Item> result = DialogFilter.filteredCopy(
                telegramList, item -> DialogKey.of(0, item.id), config, true);

        assertNotSame(telegramList, result);
        assertEquals(2, result.size());
    }

    @Test
    public void unknownItemIsPreservedFailOpen() {
        List<Item> telegramList = Arrays.asList(new Item(1), new Item(2));
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        List<Item> result = DialogFilter.filteredCopy(
                telegramList,
                item -> {
                    if (item.id == 1) throw new IllegalStateException("unknown Telegram object");
                    return DialogKey.of(0, item.id);
                },
                config,
                false);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).id);
    }

    @Test
    public void pairedCopyRemovesHiddenItemsAndAlignedMetadata() {
        Item visible = new Item(1);
        Item hidden = new Item(2);
        List<Item> items = new ArrayList<>(Arrays.asList(visible, hidden, visible));
        List<String> names = new ArrayList<>(Arrays.asList("first", "hidden", "duplicate"));
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        DialogFilter.PairedCopy<Item, String> result = DialogFilter.filteredPairedCopy(
                items, names, item -> DialogKey.of(0, item.id), config, false);

        assertNotSame(items, result.items());
        assertNotSame(names, result.metadata());
        assertEquals(Arrays.asList(visible, visible), result.items());
        assertEquals(Arrays.asList("first", "duplicate"), result.metadata());
        assertEquals(3, items.size());
        assertEquals(3, names.size());
    }

    @Test
    public void pairedCopyMismatchFailsOpenWithUntouchedCopies() {
        List<Item> items = Arrays.asList(new Item(1), new Item(2));
        List<String> names = java.util.Collections.singletonList("first");
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        DialogFilter.PairedCopy<Item, String> result = DialogFilter.filteredPairedCopy(
                items, names, item -> DialogKey.of(0, item.id), config, false);

        assertNotSame(items, result.items());
        assertNotSame(names, result.metadata());
        assertEquals(items, result.items());
        assertEquals(names, result.metadata());
    }

    @Test
    public void pairedCopyUsesExplicitAccountAndKeepsUnknownRows() {
        List<Item> items = Arrays.asList(new Item(2), new Item(0));
        List<String> names = Arrays.asList("other account", "synthetic");
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        DialogFilter.PairedCopy<Item, String> result = DialogFilter.filteredPairedCopy(
                items,
                names,
                item -> item.id == 0 ? null : DialogKey.of(1, item.id),
                config,
                false);

        assertEquals(items, result.items());
        assertEquals(names, result.metadata());
    }

    @Test
    public void pairedCopyRevealReturnsEquivalentCopies() {
        List<Item> items = Arrays.asList(new Item(1), new Item(2));
        List<String> names = Arrays.asList("visible", "hidden");
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        DialogFilter.PairedCopy<Item, String> result = DialogFilter.filteredPairedCopy(
                items, names, item -> DialogKey.of(0, item.id), config, true);

        assertNotSame(items, result.items());
        assertNotSame(names, result.metadata());
        assertEquals(items, result.items());
        assertEquals(names, result.metadata());
    }

    @Test
    public void visiblePositionsPreserveHeadersUnknownRowsAndOrder() {
        List<Item> rows = Arrays.asList(null, new Item(1), new Item(2), new Item(0), new Item(3));
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        int[] positions = DialogFilter.visiblePositions(
                rows.size(),
                rows::get,
                item -> {
                    if (item == null) return Optional.empty();
                    if (item.id == 0) throw new IllegalStateException("unknown row");
                    return Optional.of(DialogKey.of(0, item.id));
                },
                config,
                false);

        assertEquals(Arrays.asList(0, 1, 3, 4), boxed(positions));
    }

    @Test
    public void visiblePositionsRevealReturnsIdentityWithoutReadingRows() {
        HiddenConfig config = HiddenConfig.fromStrings(
                java.util.Collections.singleton("0:2"), false);

        int[] positions = DialogFilter.visiblePositions(
                3,
                index -> {
                    throw new AssertionError("reveal should bypass extraction");
                },
                item -> Optional.empty(),
                config,
                true);

        assertEquals(Arrays.asList(0, 1, 2), boxed(positions));
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }
}
