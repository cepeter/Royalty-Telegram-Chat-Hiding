package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class HiddenConfigTest {
    @Test
    public void snapshotIsImmutableAndDropsInvalidValues() {
        Set<String> raw = new HashSet<>(Arrays.asList("0:12", "1:-99", "bad", "0:0"));
        HiddenConfig config = HiddenConfig.fromStrings(raw, true);
        raw.clear();

        assertEquals(2, config.hiddenDialogs().size());
        assertTrue(config.isHidden(DialogKey.of(0, 12)));
        assertTrue(config.suppressNotifications());
        assertThrows(UnsupportedOperationException.class,
                () -> config.hiddenDialogs().add(DialogKey.of(0, 44)));
    }

    @Test
    public void emptyConfigHidesNothing() {
        HiddenConfig config = HiddenConfig.empty();

        assertFalse(config.isHidden(DialogKey.of(0, 12)));
        assertFalse(config.suppressNotifications());
        assertFalse(config.localPremium());
    }

    @Test
    public void localPremiumIsExplicitAndDisabledByDefault() {
        HiddenConfig enabled = HiddenConfig.fromStrings(
                java.util.Collections.emptySet(), false, true);
        HiddenConfig legacy = HiddenConfig.fromStrings(
                java.util.Collections.emptySet(), false);

        assertTrue(enabled.localPremium());
        assertFalse(legacy.localPremium());
    }
}
