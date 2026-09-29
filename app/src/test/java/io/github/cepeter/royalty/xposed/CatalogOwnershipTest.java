package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;
import io.github.cepeter.royalty.core.AccountInventory;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class CatalogOwnershipTest {
    @Test public void ownerReplacementDropsOldRowsAndIncludesActiveEmptyAccount() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        store.replaceAccount(0, 101, new long[] {42}, new String[] {"Old"});
        store.setInventory(new AccountInventory(Collections.singletonMap(0,
                new AccountInventory.Owner(202, "New")), true, ""));
        CatalogSnapshotStore.Snapshot snapshot = store.snapshot();
        assertEquals(202, snapshot.accounts().get(0).ownerId());
        assertEquals(0, snapshot.accounts().get(0).ids().length);
        assertTrue(snapshot.inventoryComplete());
    }
    @Test public void ownerSwitchDuringRowCollectionDiscardsRowsInsteadOfRelabelingThem() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        AccountInventory alice = new AccountInventory(Collections.singletonMap(0,
                new AccountInventory.Owner(101, "Alice")), true, "");
        AccountInventory bob = new AccountInventory(Collections.singletonMap(0,
                new AccountInventory.Owner(202, "Bob")), true, "");
        AtomicReference<AccountInventory> current = new AtomicReference<>(alice);
        CatalogOwnerPublisher.publish(0, Arrays.asList(42L), new CatalogOwnerPublisher.RowReader<Long>() {
            @Override public long id(Long row) { return row; }
            @Override public String title(Long row, long id) {
                current.set(bob); // Replacement occurs while reading A's controller rows.
                return "Alice's chat";
            }
        }, current::get, store);
        assertEquals(202, store.snapshot().accounts().get(0).ownerId());
        assertEquals(0, store.snapshot().accounts().get(0).ids().length);
    }

    @Test public void staleRowPublisherCannotReintroduceReplacedOwner() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        store.setInventory(new AccountInventory(Collections.singletonMap(0,
                new AccountInventory.Owner(202, "New")), true, ""));
        store.replaceAccount(0, 101, new long[] {42}, new String[] {"Old"});
        assertEquals(202, store.snapshot().accounts().get(0).ownerId());
        assertEquals(0, store.snapshot().accounts().get(0).ids().length);
    }

    @Test public void incompleteInventoryCannotConfirmLogout() {
        CatalogSnapshotStore store = new CatalogSnapshotStore();
        store.replaceAccount(0, 101, new long[] {42}, new String[] {"Old"});
        store.setInventory(AccountInventory.incomplete("config not loaded"));
        assertFalse(store.snapshot().inventoryComplete());
        assertEquals(101, store.snapshot().accounts().get(0).ownerId());
    }
}
