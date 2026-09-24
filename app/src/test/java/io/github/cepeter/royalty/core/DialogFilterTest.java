package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
}
