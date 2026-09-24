package io.github.cepeter.royalty.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CatalogSubmission {
    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_TITLE_LENGTH = 256;

    private CatalogSubmission() {}

    public static List<CatalogEntry> sanitize(int account, long[] ids, String[] titles) {
        if (ids == null || titles == null || ids.length != titles.length) {
            throw new IllegalArgumentException("ids and titles must be non-null and equal length");
        }
        if (account < 0 || account > 15) {
            throw new IllegalArgumentException("account must be between 0 and 15");
        }

        Map<DialogKey, CatalogEntry> entries = new LinkedHashMap<>();
        int limit = Math.min(ids.length, MAX_ENTRIES);
        for (int index = 0; index < limit; index++) {
            if (ids[index] == 0) {
                continue;
            }
            DialogKey key = DialogKey.of(account, ids[index]);
            String title = sanitizeTitle(titles[index], key.toString());
            entries.put(key, new CatalogEntry(key, title));
        }
        return new ArrayList<>(entries.values());
    }

    private static String sanitizeTitle(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        StringBuilder clean = new StringBuilder(Math.min(value.length(), MAX_TITLE_LENGTH));
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length() && clean.length() < MAX_TITLE_LENGTH; index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                if (!previousWhitespace && clean.length() > 0) {
                    clean.append(' ');
                }
                previousWhitespace = true;
            } else {
                clean.append(character);
                previousWhitespace = false;
            }
        }

        int length = clean.length();
        while (length > 0 && clean.charAt(length - 1) == ' ') {
            clean.setLength(--length);
        }
        if (length > 0 && Character.isHighSurrogate(clean.charAt(length - 1))) {
            clean.setLength(--length);
        }
        return clean.length() == 0 ? fallback : clean.toString();
    }
}
