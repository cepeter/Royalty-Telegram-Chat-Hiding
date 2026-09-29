package io.github.cepeter.royalty.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selection projection independent of saved draft and refresh timing. */
public final class CatalogSelection {
    private CatalogSelection() {}
    public static List<CatalogEntry> filter(List<CatalogEntry> catalog, HiddenConfig config,
            String rawQuery, int account, boolean hiddenOnly) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        List<CatalogEntry> result = new ArrayList<>();
        for (CatalogEntry entry : catalog) {
            if (account >= 0 && entry.key().account() != account) continue;
            if (hiddenOnly && !config.isHidden(entry.key())) continue;
            if (!query.isEmpty() && !entry.title().toLowerCase(Locale.ROOT).contains(query)
                    && !Long.toString(entry.key().dialogId()).contains(query)) continue;
            result.add(entry);
        }
        return result;
    }
    public static int selectedCount(HiddenConfig config) { return config.hiddenDialogs().size(); }
    public static void selectMatching(ConfigurationDraft draft, List<CatalogEntry> matching, boolean selected) {
        draft.setHiddenBatch(matching, selected);
    }
}
