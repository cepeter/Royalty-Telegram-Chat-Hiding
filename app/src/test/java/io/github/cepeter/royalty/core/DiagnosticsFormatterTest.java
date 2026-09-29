package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class DiagnosticsFormatterTest {
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
