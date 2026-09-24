package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import io.github.cepeter.royalty.core.CatalogSubmission;
import org.junit.Test;

public final class CatalogSnapshotStoreTest {
    @Test
    public void snapshotsAreDeepCopiesAndAccountsAreIsolated() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        long[] ids = {1};
        String[] titles = {"one"};
        store.replaceAccount(0, ids, titles);
        store.replaceAccount(1, new long[] {10}, new String[] {"ten"});
        CatalogSnapshotStore.Snapshot first = store.snapshot();
        ids[0] = 99;
        titles[0] = "changed";
        store.replaceAccount(0, new long[] {2}, new String[] {"two"});

        assertEquals(1L, first.accounts().get(0).ids()[0]);
        assertEquals("one", first.accounts().get(0).titles()[0]);
        assertEquals(10L, first.accounts().get(1).ids()[0]);
        assertThrows(UnsupportedOperationException.class,
                () -> first.accounts().put(2, first.accounts().get(0)));
    }

    @Test
    public void appliesCatalogBoundsAndSanitization() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        int count = CatalogSubmission.MAX_ENTRIES + 1;
        long[] ids = new long[count];
        String[] titles = new String[count];
        for (int index = 0; index < count; index++) {
            ids[index] = index + 1;
            titles[index] = "x".repeat(CatalogSubmission.MAX_TITLE_LENGTH + 1);
        }
        store.replaceAccount(0, ids, titles);
        CatalogSnapshotStore.AccountSnapshot account = store.snapshot().accounts().get(0);
        assertEquals(CatalogSubmission.MAX_ENTRIES, account.ids().length);
        assertEquals(CatalogSubmission.MAX_TITLE_LENGTH, account.titles()[0].length());
    }

    @Test
    public void validatesAndBoundsStatuses() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        store.recordStatus("dialogs", "installed", "x".repeat(300));
        assertEquals(256, store.snapshot().statuses().get("dialogs").detail().length());
        assertThrows(IllegalArgumentException.class,
                () -> store.recordStatus("Bad Hook", "installed", ""));
        assertThrows(IllegalArgumentException.class,
                () -> store.recordStatus("dialogs", "Bad Status", ""));
    }
}
