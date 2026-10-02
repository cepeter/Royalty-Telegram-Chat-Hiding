package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public final class DiagnosticLogTest {
    @Test public void recordsBoundedEventsAndDropsOldest() {
        DiagnosticLog log = new DiagnosticLog(2);
        log.record(1000, "refresh_started");
        log.record(2000, "refresh_timed_out");
        log.record(3000, "refresh_succeeded");
        String export = log.export("3.1.9", "12.10.4 (70992)",
                Collections.singletonMap("search", "installed"), 3000);
        assertFalse(export.contains("refresh_started"));
        assertTrue(export.contains("refresh_timed_out"));
        assertTrue(export.contains("refresh_succeeded"));
        assertTrue(export.contains("search: installed"));
    }

    @Test public void rejectsUnexpectedEventsAndSanitizesUntrustedStatusValues() {
        DiagnosticLog log = new DiagnosticLog(4);
        assertThrows(IllegalArgumentException.class, () -> log.record(0, "chat title"));
        String export = log.export("3.1.9", "12.10.4 (70992)",
                Map.of("search", "installed\nsecret-chat", "contacts", "secret_chat"), 0);
        assertTrue(export.contains("search: unavailable"));
        assertTrue(export.contains("contacts: unavailable"));
        assertFalse(export.contains("secret-chat"));
    }

    @Test public void sanitizesUntrustedVersionValues() {
        DiagnosticLog log = new DiagnosticLog(4);
        String export = log.export("3.1.9\nsecret", "12.10.4\nsecret",
                Collections.emptyMap(), 0);
        assertFalse(export.contains("secret"));
        assertTrue(export.contains("Module: unavailable"));
        assertTrue(export.contains("Telegram: unavailable"));
    }

    @Test public void statusChangesDoNotRepeatUnchangedObservations() {
        DiagnosticLog log = new DiagnosticLog(4);
        log.observe(1000, Collections.singletonMap("search", "missing"));
        log.observe(2000, Collections.singletonMap("search", "missing"));
        log.observe(3000, Collections.singletonMap("search", "installed"));
        String export = log.export("3.1.9", "12.10.4 (70992)",
                Collections.emptyMap(), 3000);
        assertEquals(1, export.split("search=missing", -1).length - 1);
        assertEquals(1, export.split("search=installed", -1).length - 1);
    }
}
