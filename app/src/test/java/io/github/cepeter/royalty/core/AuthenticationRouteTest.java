package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AuthenticationRouteTest {
    @Test public void componentPackageTracksInstalledModuleWhileClassRemainsStable() {
        assertEquals("io.github.cepeter.royalty", AuthenticationRoute.packageName("io.github.cepeter.royalty"));
        assertEquals("io.github.cepeter.royalty.debug", AuthenticationRoute.packageName("io.github.cepeter.royalty.debug"));
        assertEquals("io.github.cepeter.royalty.AuthenticationActivity", AuthenticationRoute.className());
    }
}
