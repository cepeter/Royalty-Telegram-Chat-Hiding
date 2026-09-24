package io.github.cepeter.royalty.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class DialogFilter {
    private DialogFilter() {}

    public static <T> List<T> filteredCopy(
            List<T> source,
            Function<T, DialogKey> keyExtractor,
            HiddenConfig config,
            boolean reveal) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Objects.requireNonNull(config, "config");

        if (reveal || config.hiddenDialogs().isEmpty()) {
            return new ArrayList<>(source);
        }

        List<T> result = new ArrayList<>(source.size());
        for (T item : source) {
            try {
                DialogKey key = keyExtractor.apply(item);
                if (!config.isHidden(key)) {
                    result.add(item);
                }
            } catch (RuntimeException unknownTelegramObject) {
                // Unknown Telegram objects stay visible rather than risking data loss.
                result.add(item);
            }
        }
        return result;
    }

    public static <T, M> PairedCopy<T, M> filteredPairedCopy(
            List<T> items,
            List<M> metadata,
            Function<T, DialogKey> keyExtractor,
            HiddenConfig config,
            boolean reveal) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Objects.requireNonNull(config, "config");

        if (items.size() != metadata.size()
                || reveal
                || config.hiddenDialogs().isEmpty()) {
            return new PairedCopy<>(new ArrayList<>(items), new ArrayList<>(metadata));
        }

        List<T> filteredItems = new ArrayList<>(items.size());
        List<M> filteredMetadata = new ArrayList<>(metadata.size());
        for (int index = 0; index < items.size(); index++) {
            T item = items.get(index);
            try {
                if (config.isHidden(keyExtractor.apply(item))) {
                    continue;
                }
            } catch (RuntimeException unknownTelegramObject) {
                // Unknown Telegram objects stay visible rather than risking data loss.
            }
            filteredItems.add(item);
            filteredMetadata.add(metadata.get(index));
        }
        return new PairedCopy<>(filteredItems, filteredMetadata);
    }

    public static final class PairedCopy<T, M> {
        private final List<T> items;
        private final List<M> metadata;

        private PairedCopy(List<T> items, List<M> metadata) {
            this.items = items;
            this.metadata = metadata;
        }

        public List<T> items() {
            return items;
        }

        public List<M> metadata() {
            return metadata;
        }
    }
}
