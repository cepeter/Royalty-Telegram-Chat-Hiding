package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.catalog.CatalogProtocol;
import io.github.cepeter.royalty.core.CatalogEntry;
import io.github.cepeter.royalty.core.CatalogSubmission;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CatalogSnapshotStore {
    private final Map<Integer, AccountSnapshot> accounts = new LinkedHashMap<>();
    private final Map<String, StatusSnapshot> statuses = new TreeMap<>();

    public synchronized void replaceAccount(int account, long[] ids, String[] titles) {
        List<CatalogEntry> entries = CatalogSubmission.sanitize(account, ids, titles);
        long[] cleanIds = new long[entries.size()];
        String[] cleanTitles = new String[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            CatalogEntry entry = entries.get(index);
            cleanIds[index] = entry.key().dialogId();
            cleanTitles[index] = entry.title();
        }
        accounts.put(account, new AccountSnapshot(cleanIds, cleanTitles));
    }

    public synchronized void recordStatus(String hook, String status, String detail) {
        String safeHook = requireStatusToken(hook, "hook");
        String safeStatus = requireStatusToken(status, "status");
        String safeDetail = detail == null ? "" : detail;
        if (safeDetail.length() > CatalogProtocol.MAX_STATUS_DETAIL_LENGTH) {
            safeDetail = safeDetail.substring(0, CatalogProtocol.MAX_STATUS_DETAIL_LENGTH);
        }
        statuses.put(safeHook, new StatusSnapshot(safeStatus, safeDetail));
    }

    public synchronized Snapshot snapshot() {
        Map<Integer, AccountSnapshot> accountCopy = new LinkedHashMap<>();
        for (Map.Entry<Integer, AccountSnapshot> entry : accounts.entrySet()) {
            accountCopy.put(entry.getKey(), entry.getValue().copy());
        }
        return new Snapshot(accountCopy, new TreeMap<>(statuses));
    }

    private static String requireStatusToken(String value, String name) {
        if (value == null || value.isEmpty()
                || value.length() > CatalogProtocol.MAX_STATUS_NAME_LENGTH) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'a' && character <= 'z') || character == '_')) {
                throw new IllegalArgumentException(name + " contains invalid characters");
            }
        }
        return value;
    }

    public static final class Snapshot {
        private final Map<Integer, AccountSnapshot> accounts;
        private final Map<String, StatusSnapshot> statuses;

        private Snapshot(
                Map<Integer, AccountSnapshot> accounts,
                Map<String, StatusSnapshot> statuses) {
            this.accounts = Collections.unmodifiableMap(accounts);
            this.statuses = Collections.unmodifiableMap(statuses);
        }

        public Map<Integer, AccountSnapshot> accounts() {
            return accounts;
        }

        public Map<String, StatusSnapshot> statuses() {
            return statuses;
        }
    }

    public static final class AccountSnapshot {
        private final long[] ids;
        private final String[] titles;

        private AccountSnapshot(long[] ids, String[] titles) {
            this.ids = java.util.Arrays.copyOf(ids, ids.length);
            this.titles = java.util.Arrays.copyOf(titles, titles.length);
        }

        public long[] ids() {
            return java.util.Arrays.copyOf(ids, ids.length);
        }

        public String[] titles() {
            return java.util.Arrays.copyOf(titles, titles.length);
        }

        private AccountSnapshot copy() {
            return new AccountSnapshot(ids, titles);
        }
    }

    public static final class StatusSnapshot {
        private final String status;
        private final String detail;

        private StatusSnapshot(String status, String detail) {
            this.status = status;
            this.detail = detail;
        }

        public String status() {
            return status;
        }

        public String detail() {
            return detail;
        }
    }
}
