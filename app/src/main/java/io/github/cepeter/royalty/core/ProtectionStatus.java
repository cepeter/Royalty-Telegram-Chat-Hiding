package io.github.cepeter.royalty.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Evaluates each required surface and the optional Premium hook independently. */
public final class ProtectionStatus {
    public static final long MAX_AGE_MILLIS = 60_000L;
    public static final String[] REQUIRED = {"bridge", "compatibility", "dialogs", "search",
            "contacts", "share", "notifications", "reveal", "ownership"};
    public enum State { HEALTHY, MISSING, DEGRADED, STALE, UNSUPPORTED }

    private final Map<String, State> surfaces;
    private final State premium;

    private ProtectionStatus(Map<String, State> surfaces, State premium) {
        this.surfaces = Collections.unmodifiableMap(surfaces);
        this.premium = premium;
    }

    public static ProtectionStatus evaluate(Map<String, String> statuses, Map<String, String> details,
            long observedAtMillis, long nowMillis, boolean refreshTimedOut) {
        boolean stale = refreshTimedOut || observedAtMillis <= 0 || nowMillis < observedAtMillis
                || nowMillis - observedAtMillis > MAX_AGE_MILLIS;
        Map<String, State> surfaces = new LinkedHashMap<>();
        for (String key : REQUIRED) surfaces.put(key, classify(statuses.get(key), details.get(key), stale));
        return new ProtectionStatus(surfaces, classify(statuses.get("premium"), details.get("premium"), stale));
    }

    private static State classify(String value, String detail, boolean stale) {
        if ("unsupported_version".equals(value)) return State.UNSUPPORTED;
        if (value == null) return State.MISSING;
        if (stale) return State.STALE;
        if (!"installed".equals(value) || (detail != null && detail.contains("unknown_rows_visible"))) {
            return State.DEGRADED;
        }
        return State.HEALTHY;
    }

    public Map<String, State> surfaces() { return surfaces; }
    public State surface(String key) { return surfaces.get(key); }
    public State premium() { return premium; }
    public boolean working() { return !surfaces.containsValue(State.MISSING)
            && !surfaces.containsValue(State.DEGRADED) && !surfaces.containsValue(State.STALE)
            && !surfaces.containsValue(State.UNSUPPORTED); }
    public boolean unsupported() { return surfaces.containsValue(State.UNSUPPORTED); }
}
