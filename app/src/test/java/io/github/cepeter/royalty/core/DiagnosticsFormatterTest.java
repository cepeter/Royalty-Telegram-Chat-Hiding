package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class DiagnosticsFormatterTest {
    @Test public void missingAndStaleRequiredSurfacesHaveRecoveryGuidance() {
        String text = DiagnosticsFormatter.describe(Collections.singletonMap("search", "installed"),
                Collections.emptyMap(), 1000, 100000, false, "", "12.10.4 (70992)");
        assertTrue(text.contains("● search: STALE")); assertTrue(text.contains("● bridge: MISSING"));
        assertTrue(text.contains("Open Telegram")); assertTrue(text.contains("Refresh"));
    }

    @Test public void unopenedTelegramIsReportedAsWaitingNotHookFailure() {
        String text = DiagnosticsFormatter.describe(Collections.emptyMap(), Collections.emptyMap(),
                0, 10_000, false, "Catalog response timed out", "12.10.4 (70992)");
        assertTrue(text.contains("● bridge: WAITING"));
        assertTrue(text.contains("Telegram is not running or has not been opened"));
        assertFalse(text.contains("● bridge: MISSING"));
        assertFalse(text.contains("Enable Royalty for Telegram in LSPosed"));
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

    @Test public void everySurfaceHasABulletedStateLabel() {
        Map<String, String> statuses = new HashMap<>();
        for (String key : ProtectionStatus.REQUIRED) statuses.put(key, "installed");
        statuses.put("premium", "installed");
        String text = DiagnosticsFormatter.describe(statuses, Collections.emptyMap(),
                1000, 1100, false, "", "12.10.4 (70992)");
        for (String key : ProtectionStatus.REQUIRED)
            assertTrue(text.contains("● " + key + ": HEALTHY"));
        assertTrue(text.contains("● Optional Premium: HEALTHY"));
    }

    @Test public void semanticCompatibilityDetailDoesNotCallUntestedBuildUnsupported() {
        Map<String, String> statuses = new HashMap<>();
        for (String key : ProtectionStatus.REQUIRED) statuses.put(key, "installed");
        Map<String, String> details = Collections.singletonMap(
                "compatibility", "semantic profile for 12.10.5 (71000)");
        String text = DiagnosticsFormatter.describe(statuses, details, 1000, 1100,
                false, "", "12.10.5 (71000)");
        assertTrue(text.contains("semantic profile for 12.10.5 (71000)"));
        assertFalse(text.contains("Use exactly Telegram 12.10.4"));
    }
}
