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
}
