package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.AccountInventory;
import io.github.cepeter.royalty.core.CatalogSubmission;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Collects one controller's rows only while its slot has the same confirmed owner. */
final class CatalogOwnerPublisher {
    interface RowReader<T> {
        long id(T row) throws Throwable;
        String title(T row, long id) throws Throwable;
    }

    private CatalogOwnerPublisher() {}

    static <T> void publish(int slot, List<T> rows, RowReader<T> reader,
            Supplier<AccountInventory> owners, CatalogSnapshotStore store) {
        AccountInventory before = owners.get();
        AccountInventory.Owner source = before.owner(slot);
        if (!before.complete() || source == null) {
            store.setInventory(before);
            return;
        }

        int count = Math.min(rows.size(), CatalogSubmission.MAX_ENTRIES);
        long[] ids = new long[count];
        String[] titles = new String[count];
        int added = 0;
        for (int index = 0; index < count; index++) {
            try {
                T row = rows.get(index);
                long id = reader.id(row);
                if (id == 0) continue;
                ids[added] = id;
                titles[added] = reader.title(row, id);
                added++;
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable ignored) {
                // Synthetic or unknown Telegram rows are not hideable dialogs.
            }
        }
        AccountInventory after = owners.get();
        store.setInventory(after);
        if (!after.complete() || !after.matches(slot, source.id())) return;
        store.replaceAccount(slot, source.id(), Arrays.copyOf(ids, added), Arrays.copyOf(titles, added));
    }
}
