package io.github.cepeter.royalty.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UpdateReleaseTest {
    private static final String RELEASE_URL =
            "https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.1.0";

    @Test
    public void acceptsTrustedStableRelease() {
        UpdateRelease release = UpdateRelease.fromMetadata(
                "v3.1.0", RELEASE_URL, false, false);

        assertEquals("v3.1.0", release.tag());
        assertEquals("3.1.0", release.version());
        assertEquals(RELEASE_URL, release.url());
    }

    @Test
    public void rejectsDraftPrereleaseMalformedAndUntrustedResults() {
        assertNull(UpdateRelease.fromMetadata("v3.1.0", RELEASE_URL, true, false));
        assertNull(UpdateRelease.fromMetadata("v3.1.0", RELEASE_URL, false, true));
        assertNull(UpdateRelease.fromMetadata("latest", RELEASE_URL, false, false));
        assertNull(UpdateRelease.fromMetadata(
                "v3.1.0", "https://example.com/app.apk", false, false));
        assertNull(UpdateRelease.fromMetadata("", "", false, false));
        assertNull(UpdateRelease.fromStored(null, null));
    }

    @Test
    public void restoresOnlyValidatedCachedMetadata() {
        assertEquals("v3.1.0", UpdateRelease.fromStored("v3.1.0", RELEASE_URL).tag());
        assertNull(UpdateRelease.fromStored("v3.1.0", "https://example.com/v3.1.0"));
    }

    @Test
    public void comparesNumericVersionsAndIgnoresDebugSuffix() {
        UpdateRelease release = UpdateRelease.fromMetadata(
                "v3.10.0",
                "https://github.com/cepeter/Royalty-Telegram-Chat-Hiding/releases/tag/v3.10.0",
                false,
                false);

        assertTrue(release.isNewerThan("3.9.9"));
        assertFalse(release.isNewerThan("3.10.0"));
        assertFalse(release.isNewerThan("3.10.1"));
        assertTrue(release.isNewerThan("3.9.9-debug"));
        assertFalse(release.isNewerThan("invalid"));
    }
}
