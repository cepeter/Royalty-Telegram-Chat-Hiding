package io.github.cepeter.royalty.core;

import java.util.Map;

/** Evaluated observations and recovery steps; never a physical acceptance certificate. */
public final class DiagnosticsFormatter {
    private DiagnosticsFormatter() {}
    public static String describe(Map<String, String> statuses, Map<String, String> details,
            long observedAt, long now, boolean checking, String error, String installedVersion) {
        return describe(statuses, details, observedAt, now, checking, error, installedVersion, "");
    }
    public static String describe(Map<String, String> statuses, Map<String, String> details,
            long observedAt, long now, boolean checking, String error, String installedVersion, String session) {
        StringBuilder text = new StringBuilder("Installed Telegram: ").append(installedVersion)
                .append(" · Supported: 12.10.4 (70992)");
        if (checking) text.append("\nChecking Telegram… Previous observations follow; check not complete.");
        if (observedAt > 0 && now >= observedAt)
            text.append("\nLast known response: ").append((now - observedAt) / 1000).append("s ago");
        else text.append("\nNo confirmed response yet");
        text.append("\nConfirmed main-process session: ")
                .append(session == null || session.isEmpty() ? "unavailable" : session.substring(0, Math.min(64, session.length())));
        boolean failed = error != null && !error.isEmpty();
        if (failed) text.append("\nRefresh error: ").append(error)
                .append(". Open Telegram and Refresh; expired or incomplete responses preserve the last confirmed cache.");
        ProtectionStatus evaluated = ProtectionStatus.evaluate(statuses, details, observedAt, now, failed);
        for (Map.Entry<String, ProtectionStatus.State> surface : evaluated.surfaces().entrySet()) {
            String key = surface.getKey();
            text.append("\n").append(key).append(": ").append(surface.getValue());
            if (statuses.containsKey(key)) text.append(" (reported ").append(statuses.get(key)).append(')');
            String detail = details.get(key);
            if (detail != null && !detail.isEmpty()) text.append(" — ").append(detail);
            switch (surface.getValue()) {
                case MISSING:
                    text.append(". Enable Royalty for Telegram in LSPosed, restart Telegram, then Refresh."); break;
                case STALE:
                    text.append(". Open Telegram and Refresh for a current observation."); break;
                case UNSUPPORTED:
                    text.append(". Use exactly Telegram 12.10.4 (70992); other versions have no verified profile."); break;
                case DEGRADED:
                    text.append(". Restart the supported Telegram build and Refresh. If fallback or incomplete scan persists, review this surface manually; concealment is not fully confirmed."); break;
                default: break;
            }
        }
        text.append("\nOptional Premium: ").append(evaluated.premium());
        text.append("\nManual device checks pending: test two accounts, logout/replacement, empty account, lists, search, share, contacts, and notifications on the supported build.");
        return text.toString();
    }
}
