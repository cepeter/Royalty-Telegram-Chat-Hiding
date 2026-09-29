package io.github.cepeter.royalty.catalog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.CatalogEntry;
import io.github.cepeter.royalty.core.AccountInventory;
import io.github.cepeter.royalty.core.DialogKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogRepository {
    private static final String PREFERENCES_NAME = "catalog";
    private static final String DIALOG_PREFIX = "dialog.";
    private static final String STATUS_PREFIX = "status.";
    private static final String ROW_OWNER_PREFIX = "row_owner.";
    private static final String ACCOUNT_OWNER_PREFIX = "account_owner.";
    private static final String ACCOUNT_LABEL_PREFIX = "account_label.";
    private static final String INVENTORY_COMPLETE = "inventory_complete";
    private static final String PROCESS_SESSION = "process_session";
    private static final String OBSERVED_AT = "observed_at";
    // Context instances share the same local SharedPreferences object within this process.
    // Keep confirmed data here because a failed commit may still change its in-memory map.
    private static final Map<SharedPreferences, Map<String, ?>> LAST_GOOD = new IdentityHashMap<>();

    private final SharedPreferences preferences;

    public CatalogRepository(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    CatalogRepository(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @SuppressLint("ApplySharedPref")
    public boolean replaceSnapshot(CatalogBatch.Snapshot snapshot) {
        synchronized (LAST_GOOD) {
            Map<String, Object> confirmed = new HashMap<>(confirmedView());
            confirmed.keySet().removeIf(key -> key.startsWith(STATUS_PREFIX)
                    || ((snapshot.inventory().complete() || snapshot.legacyProtocol()) && (key.startsWith(DIALOG_PREFIX)
                    || key.startsWith(ROW_OWNER_PREFIX) || key.startsWith(ACCOUNT_OWNER_PREFIX)
                    || key.startsWith(ACCOUNT_LABEL_PREFIX))));
            if (snapshot.inventory().complete()) for (Map.Entry<Integer, AccountInventory.Owner> owner
                    : snapshot.inventory().owners().entrySet()) {
                String idKey = ACCOUNT_OWNER_PREFIX + owner.getKey();
                String labelKey = ACCOUNT_LABEL_PREFIX + owner.getKey();
                confirmed.put(idKey, owner.getValue().id());
                confirmed.put(labelKey, owner.getValue().label());
            }
            if (snapshot.inventory().complete() || snapshot.legacyProtocol()) for (List<CatalogEntry> entries : snapshot.accounts().values()) {
                for (CatalogEntry entry : entries) {
                    String key = DIALOG_PREFIX + entry.key();
                    confirmed.put(key, entry.title());
                    String ownerKey = ROW_OWNER_PREFIX + entry.key();
                    confirmed.put(ownerKey, entry.ownerId());
                }
            }
            for (Map.Entry<String, CatalogBatch.Status> entry : snapshot.statuses().entrySet()) {
                String key = STATUS_PREFIX + entry.getKey();
                confirmed.put(key, entry.getValue().value());
                confirmed.put(key + ".detail", entry.getValue().detail());
            }
            confirmed.put(INVENTORY_COMPLETE, Boolean.toString(snapshot.inventory().complete()));
            confirmed.put(PROCESS_SESSION, snapshot.processSession());
            confirmed.put(OBSERVED_AT, snapshot.observedAtMillis());
            // The checked write and LAST_GOOD must describe exactly the same image.
            // Failed commits can contaminate memory, including keys absent from LAST_GOOD.
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet())
                if (!confirmed.containsKey(key)) editor.remove(key);
            for (Map.Entry<String, Object> entry : confirmed.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) editor.putString(entry.getKey(), (String) value);
                else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
                else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
                else if (value instanceof java.util.Set<?>) {
                    @SuppressWarnings("unchecked") java.util.Set<String> strings = (java.util.Set<String>) value;
                    editor.putStringSet(entry.getKey(), strings);
                }
            }
            try { if (!editor.commit()) return false; }
            catch (RuntimeException failure) { return false; }
            LAST_GOOD.put(preferences, Collections.unmodifiableMap(confirmed));
            return true;
        }
    }

    private Map<String, ?> confirmedView() {
        synchronized (LAST_GOOD) {
            return LAST_GOOD.computeIfAbsent(preferences,
                    ignored -> Collections.unmodifiableMap(new HashMap<>(preferences.getAll())));
        }
    }

    public String processSession() {
        Object value = confirmedView().get(PROCESS_SESSION);
        return value instanceof String ? (String) value : "";
    }
    public long observedAtMillis() {
        Object value = confirmedView().get(OBSERVED_AT);
        return value instanceof Long ? (Long) value : 0;
    }

    public Map<String, String> loadHookDetails() {
        Map<String, String> details = new java.util.TreeMap<>();
        for (Map.Entry<String, ?> stored : confirmedView().entrySet()) {
            if (stored.getKey().startsWith(STATUS_PREFIX) && stored.getKey().endsWith(".detail")
                    && stored.getValue() instanceof String) {
                String hook = stored.getKey().substring(STATUS_PREFIX.length(),
                        stored.getKey().length() - ".detail".length());
                details.put(hook, (String) stored.getValue());
            }
        }
        return details;
    }

    public AccountInventory loadInventory() {
        Map<Integer, AccountInventory.Owner> owners = new HashMap<>();
        Map<String, ?> view = confirmedView();
        for (Map.Entry<String, ?> stored : view.entrySet()) {
            if (!stored.getKey().startsWith(ACCOUNT_OWNER_PREFIX) || !(stored.getValue() instanceof Long)) continue;
            try {
                int slot = Integer.parseInt(stored.getKey().substring(ACCOUNT_OWNER_PREFIX.length()));
                long owner = (Long) stored.getValue();
                Object label = view.get(ACCOUNT_LABEL_PREFIX + slot);
                owners.put(slot, new AccountInventory.Owner(owner, label instanceof String ? (String) label : ""));
            } catch (IllegalArgumentException ignored) { /* corrupted inventory remains incomplete */ }
        }
        boolean complete = "true".equals(view.get(INVENTORY_COMPLETE));
        try { return new AccountInventory(owners, complete, complete ? "" : "owner inventory incomplete"); }
        catch (IllegalArgumentException error) { return AccountInventory.incomplete("invalid stored owner inventory"); }
    }

    public List<CatalogEntry> loadCatalog() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> stored : confirmedView().entrySet()) {
            if (!stored.getKey().startsWith(DIALOG_PREFIX) || !(stored.getValue() instanceof String)) {
                continue;
            }
            DialogKey.tryParse(stored.getKey().substring(DIALOG_PREFIX.length()))
                    .ifPresent(key -> {
                        Object owner = confirmedView().get(ROW_OWNER_PREFIX + key);
                        entries.add(new CatalogEntry(key, (String) stored.getValue(),
                                owner instanceof Long ? (Long) owner : 0));
                    });
        }
        Collections.sort(entries);
        return entries;
    }

    public Map<String, String> loadHookStatuses() {
        Map<String, String> statuses = new java.util.TreeMap<>();
        for (Map.Entry<String, ?> stored : confirmedView().entrySet()) {
            if (!stored.getKey().startsWith(STATUS_PREFIX)
                    || !(stored.getValue() instanceof String)) {
                continue;
            }
            String hook = stored.getKey().substring(STATUS_PREFIX.length());
            if (!hook.contains(".")) {
                statuses.put(hook, (String) stored.getValue());
            }
        }
        return statuses;
    }
}
