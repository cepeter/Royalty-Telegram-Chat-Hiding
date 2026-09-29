package io.github.cepeter.royalty.catalog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.CatalogEntry;
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
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (key.startsWith(DIALOG_PREFIX) || key.startsWith(STATUS_PREFIX)) editor.remove(key);
            }
            confirmed.keySet().removeIf(key -> key.startsWith(DIALOG_PREFIX)
                    || key.startsWith(STATUS_PREFIX));
            for (List<CatalogEntry> entries : snapshot.accounts().values()) {
                for (CatalogEntry entry : entries) {
                    String key = DIALOG_PREFIX + entry.key();
                    editor.putString(key, entry.title());
                    confirmed.put(key, entry.title());
                }
            }
            for (Map.Entry<String, CatalogBatch.Status> entry : snapshot.statuses().entrySet()) {
                String key = STATUS_PREFIX + entry.getKey();
                editor.putString(key, entry.getValue().value());
                editor.putString(key + ".detail", entry.getValue().detail());
                confirmed.put(key, entry.getValue().value());
                confirmed.put(key + ".detail", entry.getValue().detail());
            }
            editor.putString(PROCESS_SESSION, snapshot.processSession());
            editor.putLong(OBSERVED_AT, snapshot.observedAtMillis());
            confirmed.put(PROCESS_SESSION, snapshot.processSession());
            confirmed.put(OBSERVED_AT, snapshot.observedAtMillis());
            if (!editor.commit()) return false;
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

    public List<CatalogEntry> loadCatalog() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> stored : confirmedView().entrySet()) {
            if (!stored.getKey().startsWith(DIALOG_PREFIX) || !(stored.getValue() instanceof String)) {
                continue;
            }
            DialogKey.tryParse(stored.getKey().substring(DIALOG_PREFIX.length()))
                    .ifPresent(key -> entries.add(new CatalogEntry(key, (String) stored.getValue())));
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
