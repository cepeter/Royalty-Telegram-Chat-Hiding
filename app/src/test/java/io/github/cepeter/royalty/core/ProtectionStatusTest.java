package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class ProtectionStatusTest {
    private static Map<String, String> healthy() {
        Map<String, String> statuses = new HashMap<>();
        for (String key : new String[] {"bridge", "compatibility", "dialogs", "search",
                "contacts", "share", "notifications", "reveal", "ownership"}) {
            statuses.put(key, "installed");
        }
        return statuses;
    }

    @Test public void allRequiredHealthyWithoutPremiumIsWorking() {
        ProtectionStatus status = ProtectionStatus.evaluate(healthy(), new HashMap<>(), 1000, 1100, false);
        assertTrue(status.working());
        assertEquals(ProtectionStatus.State.MISSING, status.premium());
    }

    @Test public void incompleteOwnerInventoryDegradesWholeProtection() {
        Map<String, String> statuses = healthy();
        statuses.put("ownership", "runtime_error");
        assertFalse(ProtectionStatus.evaluate(statuses, new HashMap<>(), 1000, 1100, false).working());
    }

    @Test public void partialStaleUnknownAndUnsupportedAreNeverGreen() {
        Map<String, String> partial = healthy();
        partial.remove("contacts");
        assertFalse(ProtectionStatus.evaluate(partial, new HashMap<>(), 1000, 1100, false).working());
        assertFalse(ProtectionStatus.evaluate(healthy(), new HashMap<>(), 1000, 62001, false).working());
        Map<String, String> details = new HashMap<>();
        details.put("share", "unknown_rows_visible");
        ProtectionStatus degraded = ProtectionStatus.evaluate(healthy(), details, 1000, 1100, false);
        assertEquals(ProtectionStatus.State.DEGRADED, degraded.surface("share"));
        assertFalse(degraded.working());
        Map<String, String> unsupported = healthy();
        unsupported.put("compatibility", "unsupported_version");
        assertTrue(ProtectionStatus.evaluate(unsupported, new HashMap<>(), 1000, 1100, false).unsupported());
        assertFalse(ProtectionStatus.evaluate(healthy(), new HashMap<>(), 1000, 1100, true).working());
    }
}
