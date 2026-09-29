package io.github.cepeter.royalty.core;

import java.util.Map;
import java.util.TreeMap;

/** Plain-language status snapshot, including last-known age and raw failure detail. */
public final class DiagnosticsFormatter {
    private DiagnosticsFormatter() {}
    public static String describe(Map<String, String> statuses, Map<String, String> details,
            long observedAt, long now, boolean checking, String error, String installedVersion) {
        StringBuilder text = new StringBuilder();
        text.append("Installed Telegram: ").append(installedVersion)
                .append(" · Supported: 12.10.4 (70992)");
        if (checking) text.append("\nChecking Telegram…");
        if (observedAt > 0 && now >= observedAt)
            text.append("\nLast known response: ").append((now - observedAt) / 1000).append("s ago");
        else text.append("\nNo confirmed response yet");
        if (error != null && !error.isEmpty()) text.append("\nRefresh error: ").append(error);
        for (Map.Entry<String, String> status : new TreeMap<>(statuses).entrySet()) {
            text.append("\n").append(status.getKey()).append(": ").append(status.getValue());
            String detail = details.get(status.getKey());
            if (detail != null && !detail.isEmpty()) text.append(" — ").append(detail);
        }
        text.append("\nManual device checks pending: test two accounts, logout/replacement, empty account, lists, search, share, contacts, and notifications on the supported build.");
        return text.toString();
    }
}
