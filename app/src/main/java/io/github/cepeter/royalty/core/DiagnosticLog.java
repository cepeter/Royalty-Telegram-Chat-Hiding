package io.github.cepeter.royalty.core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Bounded, process-local diagnostics; never accepts chat content or throwable messages. */
public final class DiagnosticLog {
    private final int capacity;
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private Map<String, String> previousStatuses = Collections.emptyMap();

    public DiagnosticLog(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void record(long atMillis, String event) {
        if (!event.equals("refresh_started") && !event.equals("refresh_succeeded")
                && !event.equals("refresh_failed") && !event.equals("refresh_timed_out")
                && !event.equals("settings_saved") && !event.equals("settings_save_failed")) {
            throw new IllegalArgumentException("unexpected diagnostic event");
        }
        append(atMillis, event);
    }

    public synchronized void observe(long atMillis, Map<String, String> statuses) {
        Map<String, String> safe = safeStatuses(statuses);
        for (Map.Entry<String, String> entry : safe.entrySet()) {
            if (!entry.getValue().equals(previousStatuses.get(entry.getKey()))) {
                append(atMillis, entry.getKey() + "=" + entry.getValue());
            }
        }
        previousStatuses = safe;
    }

    public synchronized String export(String moduleVersion, String telegramVersion,
            Map<String, String> statuses, long observedAtMillis) {
        StringBuilder text = new StringBuilder("Royalty diagnostic log\n")
                .append("Module: ").append(safeVersion(moduleVersion)).append('\n')
                .append("Telegram: ").append(safeVersion(telegramVersion)).append('\n')
                .append("Last confirmed response: ")
                .append(observedAtMillis > 0 ? Instant.ofEpochMilli(observedAtMillis) : "none")
                .append("\nHook statuses:\n");
        for (Map.Entry<String, String> entry : safeStatuses(statuses).entrySet()) {
            text.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        text.append("Events (UTC, oldest first):\n");
        for (String event : events) text.append(event).append('\n');
        text.append("No chat titles, dialog IDs, account IDs, message contents, or raw exception text are collected.\n");
        return text.toString();
    }

    private void append(long atMillis, String event) {
        if (events.size() == capacity) events.removeFirst();
        events.addLast(Instant.ofEpochMilli(atMillis) + " " + event);
    }

    private static Map<String, String> safeStatuses(Map<String, String> source) {
        Map<String, String> safe = new TreeMap<>();
        for (String key : ProtectionStatus.REQUIRED) {
            if (source.containsKey(key)) safe.put(key, safeStatus(source.get(key)));
        }
        if (source.containsKey("premium")) safe.put("premium", safeStatus(source.get("premium")));
        return safe;
    }

    private static String safeStatus(String value) {
        if ("installed".equals(value) || "missing".equals(value)
                || "runtime_error".equals(value) || "unsupported_version".equals(value)
                || "version_read_error".equals(value) || "fallback".equals(value)) return value;
        return "unavailable";
    }

    private static String safeVersion(String value) {
        return value != null && value.matches("[A-Za-z0-9.()_ -]{1,64}") ? value : "unavailable";
    }
}
