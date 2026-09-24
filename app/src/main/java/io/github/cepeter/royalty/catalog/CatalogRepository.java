package io.github.cepeter.royalty.catalog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.core.CatalogEntry;
import io.github.cepeter.royalty.core.CatalogSubmission;
import io.github.cepeter.royalty.core.DialogKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CatalogRepository {
    private static final String PREFERENCES_NAME = "catalog";
    private static final String DIALOG_PREFIX = "dialog.";
    private static final String STATUS_PREFIX = "status.";

    private final SharedPreferences preferences;

    public CatalogRepository(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @SuppressLint("ApplySharedPref")
    public boolean replaceAccount(int account, long[] ids, String[] titles) {
        List<CatalogEntry> entries = CatalogSubmission.sanitize(account, ids, titles);
        String accountPrefix = DIALOG_PREFIX + account + ":";
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(accountPrefix)) {
                editor.remove(key);
            }
        }
        for (CatalogEntry entry : entries) {
            editor.putString(DIALOG_PREFIX + entry.key(), entry.title());
        }
        editor.putLong("catalog_updated_at", System.currentTimeMillis());
        return editor.commit();
    }

    @SuppressLint("ApplySharedPref")
    public boolean recordStatus(String hook, String status, String detail) {
        return preferences.edit()
                .putString(STATUS_PREFIX + hook, status)
                .putString(STATUS_PREFIX + hook + ".detail", detail)
                .putLong(STATUS_PREFIX + hook + ".updated_at", System.currentTimeMillis())
                .commit();
    }

    public List<CatalogEntry> loadCatalog() {
        List<CatalogEntry> entries = new ArrayList<>();
        for (Map.Entry<String, ?> stored : preferences.getAll().entrySet()) {
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
        for (Map.Entry<String, ?> stored : preferences.getAll().entrySet()) {
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
