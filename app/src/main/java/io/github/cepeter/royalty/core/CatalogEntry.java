package io.github.cepeter.royalty.core;

import java.util.Objects;

public final class CatalogEntry implements Comparable<CatalogEntry> {
    private final DialogKey key;
    private final String title;
    private final long ownerId;

    public CatalogEntry(DialogKey key, String title) {
        this(key, title, 0);
    }

    public CatalogEntry(DialogKey key, String title, long ownerId) {
        this.key = Objects.requireNonNull(key, "key");
        this.ownerId = ownerId;
        this.title = Objects.requireNonNull(title, "title");
    }

    public DialogKey key() {
        return key;
    }

    public long ownerId() { return ownerId; }

    public String title() {
        return title;
    }

    @Override
    public int compareTo(CatalogEntry other) {
        int titleOrder = String.CASE_INSENSITIVE_ORDER.compare(title, other.title);
        return titleOrder != 0 ? titleOrder : key.compareTo(other.key);
    }
}
