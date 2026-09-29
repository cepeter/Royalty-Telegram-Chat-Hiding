package io.github.cepeter.royalty.catalog;

import static org.junit.Assert.*;

import java.util.Optional;
import org.junit.Test;

public final class CatalogBatchTest {
    @Test public void completedInventoryCarriesOwnerOfRowsAndEmptyAccounts() {
        CatalogBatch batch = new CatalogBatch("owner");
        assertTrue(batch.begin("owner", new int[] {0, 1}, new long[] {101, 202},
                new String[] {"Alice", "Bob"}, true, "session"));
        assertTrue(batch.account("owner", 0, 101, new long[] {42}, new String[] {"Chat"}));
        assertTrue(batch.account("owner", 1, 202, new long[0], new String[0]));
        assertTrue(batch.status("owner", new String[0], new String[0], new String[0], "session", 100));
        CatalogBatch.Snapshot snapshot = batch.complete("owner").get();
        assertTrue(snapshot.inventory().complete());
        assertEquals(101, snapshot.accounts().get(0).get(0).ownerId());
        assertTrue(snapshot.accounts().get(1).isEmpty());
    }

    @Test public void partialBatchCannotPublish() {
        CatalogBatch batch = new CatalogBatch("a");
        assertTrue(batch.begin("a", new int[] {0}, "session"));
        assertTrue(batch.account("a", 0, new long[] {12}, new String[] {"Alice"}));
        assertFalse(batch.complete("a").isPresent());
    }

    @Test public void duplicateAndOversizedFramesInvalidateBatch() {
        CatalogBatch duplicate = new CatalogBatch("a");
        assertTrue(duplicate.begin("a", new int[] {0}, "session"));
        assertTrue(duplicate.account("a", 0, new long[] {12}, new String[] {"Alice"}));
        assertFalse(duplicate.account("a", 0, new long[] {13}, new String[] {"Bob"}));
        assertFalse(duplicate.status("a", new String[0], new String[0], new String[0], "session", 100));
        assertFalse(duplicate.complete("a").isPresent());

        CatalogBatch oversized = new CatalogBatch("b");
        assertTrue(oversized.begin("b", new int[] {0}, "session"));
        assertFalse(oversized.account("b", 0, new long[1025], new String[1025]));
        assertFalse(oversized.complete("b").isPresent());
    }

    @Test public void validEmptySnapshotCanPublishExactlyOnce() {
        CatalogBatch batch = new CatalogBatch("a");
        assertTrue(batch.begin("a", new int[0], "session"));
        assertTrue(batch.status("a", new String[0], new String[0], new String[0], "session", 100));
        Optional<CatalogBatch.Snapshot> snapshot = batch.complete("a");
        assertTrue(snapshot.isPresent());
        assertTrue(snapshot.get().accounts().isEmpty());
        assertTrue(snapshot.get().statuses().isEmpty());
        assertEquals("session", snapshot.get().processSession());
        assertEquals(100, snapshot.get().observedAtMillis());
        assertFalse(batch.complete("a").isPresent());
    }

    @Test public void rejectsMismatchedNonceAndInvalidStatusPayload() {
        CatalogBatch batch = new CatalogBatch("b");
        assertTrue(batch.begin("b", new int[0], "session"));
        assertFalse(batch.account("a", 0, new long[0], new String[0]));
        assertTrue(batch.status("b", new String[] {"search"}, new String[] {"installed"},
                new String[] {""}, "session", 100));
        assertTrue(batch.complete("b").isPresent());

        CatalogBatch invalid = new CatalogBatch("c");
        assertTrue(invalid.begin("c", new int[0], "session"));
        assertFalse(invalid.status("c", new String[] {"Bad Hook"}, new String[] {"installed"},
                new String[] {""}, "session", 100));
        assertFalse(invalid.complete("c").isPresent());
    }

    @Test public void missingAccountFrameCannotPublishDespiteStatusAndComplete() {
        CatalogBatch batch = new CatalogBatch("d");
        assertTrue(batch.begin("d", new int[] {0, 1}, "session"));
        assertTrue(batch.account("d", 0, new long[] {1}, new String[] {"One"}));
        assertTrue(batch.status("d", new String[0], new String[0], new String[0], "session", 100));
        assertFalse(batch.complete("d").isPresent());
    }

    @Test public void unexpectedAccountAndDuplicateExpectedSlotsAreRejected() {
        CatalogBatch unexpected = new CatalogBatch("e");
        assertTrue(unexpected.begin("e", new int[] {0}, "session"));
        assertFalse(unexpected.account("e", 1, new long[0], new String[0]));
        assertFalse(unexpected.complete("e").isPresent());
        CatalogBatch duplicate = new CatalogBatch("f");
        assertFalse(duplicate.begin("f", new int[] {0, 0}, "session"));
    }
}
