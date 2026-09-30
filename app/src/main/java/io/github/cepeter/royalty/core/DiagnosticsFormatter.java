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
                .append(" · Tested profile: 12.10.4 (70992); semantic resolver may validate other builds");
        if (checking) text.append("\nChecking Telegram… Previous observations follow; check not complete.");
        if (observedAt > 0 && now >= observedAt)
            text.append("\nLast known response: ").append((now - observedAt) / 1000).append("s ago");
        else text.append("\nNo confirmed response yet");
        text.append("\nConfirmed main-process session: ")
                .append(session == null || session.isEmpty() ? "unavailable"
                        : session.substring(0, Math.min(64, session.length())));
        boolean failed = error != null && !error.isEmpty();
        boolean neverObserved = statuses.isEmpty() && observedAt <= 0;
        if (failed) {
            text.append("\nRefresh error: ").append(error).append('.');
            if (neverObserved) {
                text.append(" Telegram is not running or has not been opened, or its hook process has not answered."
                        + " Open Telegram and Refresh; if it remains waiting, verify the LSPosed scope.");
            } else {
                text.append(" Open Telegram and Refresh; expired or incomplete responses preserve the last confirmed cache.");
            }
        }
        ProtectionStatus evaluated = ProtectionStatus.evaluate(statuses, details, observedAt, now, failed);
        for (Map.Entry<String, ProtectionStatus.State> surface : evaluated.surfaces().entrySet()) {
            String key = surface.getKey();
            text.append("\n").append(key).append(": ").append(surface.getValue());
            if (statuses.containsKey(key)) text.append(" (reported ").append(statuses.get(key)).append(')');
            String detail = details.get(key);
            if (detail != null && !detail.isEmpty()) text.append(" — ").append(detail);
            switch (surface.getValue()) {
                case WAITING:
                    text.append(". Telegram has not provided a hook observation yet; open Telegram and Refresh."); break;
                case MISSING:
                    text.append(". This hook surface did not report from a running Telegram process; restart Telegram and review the LSPosed scope."); break;
                case STALE:
                    text.append(". Open Telegram and Refresh for a current observation."); break;
                case UNSUPPORTED:
                    text.append(". Semantic compatibility resolution failed for this build; use the tested Telegram 12.10.4 profile or update Royalty."); break;
                case DEGRADED:
                    text.append(". Restart Telegram and Refresh. If fallback or incomplete scan persists, review this surface manually; concealment is not fully confirmed."); break;
                default: break;
            }
        }
        text.append("\nOptional Premium: ").append(evaluated.premium());
        text.append("\nManual device checks pending: test two accounts, logout/replacement, empty account, lists, search, share, contacts, and notifications on the target build.");
        return text.toString();
    }
}
