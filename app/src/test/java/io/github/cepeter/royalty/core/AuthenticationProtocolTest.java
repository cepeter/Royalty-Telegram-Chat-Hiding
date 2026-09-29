package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class AuthenticationProtocolTest {
    @Test public void onlyCanonicalOneHundredTwentyEightBitNonceIsAccepted() {
        assertTrue(AuthenticationProtocol.validNonce("0123456789abcdef0123456789abcdef"));
        assertFalse(AuthenticationProtocol.validNonce("0123456789abcdef"));
        assertFalse(AuthenticationProtocol.validNonce("0123456789ABCDEF0123456789ABCDEF"));
        assertFalse(AuthenticationProtocol.validNonce("0123456789abcdef0123456789abcdeg"));
    }
}
