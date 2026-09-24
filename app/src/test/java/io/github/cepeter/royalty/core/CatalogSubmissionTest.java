package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.junit.Test;

public final class CatalogSubmissionTest {
    @Test
    public void sanitizesTitlesAndDropsInvalidAndDuplicateIds() {
        long[] ids = {10, 0, -20, 10};
        String[] titles = {" Alice\nSmith ", "ignored", "", "Updated"};

        List<CatalogEntry> result = CatalogSubmission.sanitize(2, ids, titles);

        assertEquals(2, result.size());
        assertEquals(DialogKey.of(2, 10), result.get(0).key());
        assertEquals("Updated", result.get(0).title());
        assertEquals(DialogKey.of(2, -20), result.get(1).key());
        assertEquals("2:-20", result.get(1).title());
    }

    @Test
    public void usesArchitectureCatalogBounds() {
        assertEquals(1024, CatalogSubmission.MAX_ENTRIES);
        assertEquals(256, CatalogSubmission.MAX_TITLE_LENGTH);
    }

    @Test
    public void capsCatalogAndTitleLength() {
        long[] ids = new long[CatalogSubmission.MAX_ENTRIES + 50];
        String[] titles = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i + 1L;
            titles[i] = "x".repeat(CatalogSubmission.MAX_TITLE_LENGTH + 20);
        }

        List<CatalogEntry> result = CatalogSubmission.sanitize(0, ids, titles);

        assertEquals(CatalogSubmission.MAX_ENTRIES, result.size());
        assertEquals(CatalogSubmission.MAX_TITLE_LENGTH, result.get(0).title().length());
    }

    @Test
    public void rejectsMismatchedArraysAndInvalidAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> CatalogSubmission.sanitize(0, new long[] {1}, new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogSubmission.sanitize(16, new long[] {1}, new String[] {"one"}));
    }
}
