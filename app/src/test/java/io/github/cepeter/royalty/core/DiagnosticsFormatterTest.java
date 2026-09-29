package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class DiagnosticsFormatterTest {
    @org.junit.Test public void missingAndStaleRequiredSurfacesHaveRecoveryGuidance() {
        String text = DiagnosticsFormatter.describe(Collections.singletonMap("search", "installed"),
                Collections.emptyMap(), 1000, 100000, false, "", "12.10.4 (70992)");
        assertTrue(text.contains("search: STALE")); assertTrue(text.contains("bridge: MISSING"));
        assertTrue(text.contains("LSPosed")); assertTrue(text.contains("Refresh"));
    }

    @Test public void confirmedSessionEvidenceIsVisibleAndBounded() {
        String text = DiagnosticsFormatter.describe(Collections.emptyMap(), Collections.emptyMap(),
                1000, 2000, false, "", "12.10.4 (70992)", "session-123");
        assertTrue(text.contains("Confirmed main-process session: session-123"));
        String longSession = "x".repeat(80);
        text = DiagnosticsFormatter.describe(Collections.emptyMap(), Collections.emptyMap(),
                1000, 2000, false, "", "12.10.4 (70992)", longSession);
        assertTrue(text.contains("x".repeat(64))); assertFalse(text.contains("x".repeat(65)));
    }

    @Test public void checkingRetainsLastObservationAndReason() {
        Map<String, String> statuses = new HashMap<>();
        statuses.put("ownership", "runtime_error");
        Map<String, String> details = Collections.singletonMap("ownership", "account configuration not loaded");
        String text = DiagnosticsFormatter.describe(statuses, details, 1000, 6000,
                true, "Catalog response timed out", "12.10.4 (70992)");
        assertTrue(text.contains("Checking"));
        assertTrue(text.contains("Last known"));
        assertTrue(text.contains("5s ago"));
        assertTrue(text.contains("account configuration not loaded"));
        assertTrue(text.contains("Catalog response timed out"));
        assertTrue(text.contains("12.10.4 (70992)"));
    }
}
