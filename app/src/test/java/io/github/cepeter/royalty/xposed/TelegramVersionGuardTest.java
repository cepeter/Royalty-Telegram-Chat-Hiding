package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TelegramVersionGuardTest {
    @Test
    public void onlyVerifiedTelegramBuildIsSupported() {
        assertTrue(TelegramVersionGuard.isSupported(
                new TelegramVersionGuard.Version("12.10.4", 70992L)));
        assertFalse(TelegramVersionGuard.isSupported(
                new TelegramVersionGuard.Version("12.10.5", 70992L)));
        assertFalse(TelegramVersionGuard.isSupported(
                new TelegramVersionGuard.Version("12.10.4", 70993L)));
    }
}
