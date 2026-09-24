package io.github.cepeter.royalty.core;

import java.util.Objects;

public final class CatalogEntry implements Comparable<CatalogEntry> {
    private final DialogKey key;
    private final String title;

    public CatalogEntry(DialogKey key, String title) {
        this.key = Objects.requireNonNull(key, "key");
        this.title = Objects.requireNonNull(title, "title");
    }

    public DialogKey key() {
        return key;
    }

    public String title() {
        return title;
    }

    @Override
    public int compareTo(CatalogEntry other) {
        int titleOrder = String.CASE_INSENSITIVE_ORDER.compare(title, other.title);
        return titleOrder != 0 ? titleOrder : key.compareTo(other.key);
    }
}
