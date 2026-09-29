package io.github.cepeter.royalty.catalog;

import io.github.cepeter.royalty.core.CatalogEntry;
import io.github.cepeter.royalty.core.CatalogSubmission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/** Stages one callback sequence; no partial or invalid sequence yields a snapshot. */
public final class CatalogBatch {
    private final String nonce;
    private final Map<Integer, List<CatalogEntry>> accounts = new LinkedHashMap<>();
    private final Map<String, Status> statuses = new TreeMap<>();
    private boolean statusReceived;
    private boolean begun;
    private final Set<Integer> expectedAccounts = new HashSet<>();
    private boolean invalid;
    private boolean completed;
    private String processSession;
    private long observedAtMillis;

    public CatalogBatch(String nonce) {
        if (nonce == null || nonce.isEmpty()) throw new IllegalArgumentException("missing nonce");
        this.nonce = nonce;
    }

    public boolean begin(String receivedNonce, int[] expected, String session) {
        if (!accepts(receivedNonce)) return false;
        if (begun || expected == null || expected.length > 16 || session == null
                || session.isEmpty() || session.length() > 64) return invalidate();
        for (int account : expected) {
            if (account < 0 || account > 15 || !expectedAccounts.add(account)) return invalidate();
        }
        processSession = session;
        begun = true;
        return true;
    }

    public boolean account(String receivedNonce, int account, long[] ids, String[] titles) {
        if (!accepts(receivedNonce)) return false;
        if (!begun || statusReceived || !expectedAccounts.contains(account)
                || accounts.containsKey(account) || ids == null || titles == null
                || ids.length > CatalogSubmission.MAX_ENTRIES) return invalidate();
        try {
            accounts.put(account, Collections.unmodifiableList(CatalogSubmission.sanitize(account, ids, titles)));
            return true;
        } catch (IllegalArgumentException error) {
            return invalidate();
        }
    }

    public boolean status(String receivedNonce, String[] hooks, String[] values, String[] details,
            String session, long observedAt) {
        if (!accepts(receivedNonce)) return false;
        if (!begun || statusReceived || hooks == null || values == null || details == null
                || hooks.length != values.length || hooks.length != details.length
                || hooks.length > CatalogProtocol.MAX_STATUS_COUNT
                || !processSession.equals(session) || observedAt <= 0) return invalidate();
        try {
            for (int index = 0; index < hooks.length; index++) {
                String hook = validateToken(hooks[index]);
                String value = validateToken(values[index]);
                if (statuses.containsKey(hook)) return invalidate();
                String detail = details[index] == null ? "" : details[index];
                if (detail.length() > CatalogProtocol.MAX_STATUS_DETAIL_LENGTH) return invalidate();
                statuses.put(hook, new Status(value, detail));
            }
            observedAtMillis = observedAt;
            statusReceived = true;
            return true;
        } catch (IllegalArgumentException error) {
            return invalidate();
        }
    }

    public Optional<Snapshot> complete(String receivedNonce) {
        if (!accepts(receivedNonce)) return Optional.empty();
        completed = true;
        return begun && statusReceived && accounts.keySet().equals(expectedAccounts)
                ? Optional.of(new Snapshot(accounts, statuses, processSession,
                observedAtMillis)) : Optional.empty();
    }

    private boolean accepts(String receivedNonce) {
        return nonce.equals(receivedNonce) && !invalid && !completed;
    }

    private boolean invalidate() { invalid = true; return false; }
    public boolean invalid() { return invalid; }

    private static String validateToken(String value) {
        if (value == null || value.isEmpty() || value.length() > CatalogProtocol.MAX_STATUS_NAME_LENGTH)
            throw new IllegalArgumentException("invalid token");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!((c >= 'a' && c <= 'z') || c == '_')) throw new IllegalArgumentException("invalid token");
        }
        return value;
    }

    public static final class Snapshot {
        private final Map<Integer, List<CatalogEntry>> accounts;
        private final Map<String, Status> statuses;
        private final String processSession;
        private final long observedAtMillis;

        private Snapshot(Map<Integer, List<CatalogEntry>> accounts, Map<String, Status> statuses,
                String processSession, long observedAtMillis) {
            this.accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
            this.statuses = Collections.unmodifiableMap(new TreeMap<>(statuses));
            this.processSession = processSession;
            this.observedAtMillis = observedAtMillis;
        }
        public Map<Integer, List<CatalogEntry>> accounts() { return accounts; }
        public Map<String, Status> statuses() { return statuses; }
        public String processSession() { return processSession; }
        public long observedAtMillis() { return observedAtMillis; }
    }

    public static final class Status {
        private final String value;
        private final String detail;
        private Status(String value, String detail) { this.value = value; this.detail = detail; }
        public String value() { return value; }
        public String detail() { return detail; }
    }
}
