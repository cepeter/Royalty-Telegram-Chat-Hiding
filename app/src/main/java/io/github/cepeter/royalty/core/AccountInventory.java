package io.github.cepeter.royalty.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** A bounded, explicitly complete view of activated account owners. */
public final class AccountInventory {
    public static final int MAX_ACCOUNTS = 4;
    public static final int MAX_OWNER_LABEL_LENGTH = 128;
    public static final class Owner {
        private final long id;
        private final String label;
        public Owner(long id, String label) {
            if (id <= 0) throw new IllegalArgumentException("owner ID must be positive");
            if (label != null && label.length() > MAX_OWNER_LABEL_LENGTH)
                throw new IllegalArgumentException("owner label too long");
            this.id = id;
            this.label = label == null ? "" : label;
        }
        public long id() { return id; }
        public String label() { return label; }
    }
    private final Map<Integer, Owner> owners;
    private final boolean complete;
    private final String detail;
    public AccountInventory(Map<Integer, Owner> owners, boolean complete, String detail) {
        if (owners == null || owners.size() > MAX_ACCOUNTS) throw new IllegalArgumentException("invalid owners");
        for (Integer slot : owners.keySet()) {
            if (slot == null || slot < 0 || slot >= MAX_ACCOUNTS) throw new IllegalArgumentException("invalid slot");
        }
        this.owners = Collections.unmodifiableMap(new HashMap<>(owners));
        this.complete = complete;
        this.detail = detail == null ? "" : detail;
    }
    public static AccountInventory incomplete(String detail) {
        return new AccountInventory(Collections.emptyMap(), false, detail);
    }
    public Map<Integer, Owner> owners() { return owners; }
    public Owner owner(int slot) { return owners.get(slot); }
    public boolean complete() { return complete; }
    public String detail() { return detail; }
    public boolean confirmedAbsent(int slot) { return complete && !owners.containsKey(slot); }
    public boolean matches(int slot, long ownerId) {
        Owner owner = owners.get(slot);
        return owner != null && owner.id() == ownerId;
    }
}
