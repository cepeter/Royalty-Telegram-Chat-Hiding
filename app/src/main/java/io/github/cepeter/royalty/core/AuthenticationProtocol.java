package io.github.cepeter.royalty.core;

public final class AuthenticationProtocol {
    private AuthenticationProtocol() {}
    public static final String ACTION_RESULT = "io.github.cepeter.royalty.AUTH_RESULT";
    public static final String EXTRA_NONCE = "nonce";
    public static final String EXTRA_SUCCESS = "success";
    public static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    public static final String SIGNATURE_PERMISSION = "io.github.cepeter.royalty.permission.CATALOG_REQUEST";
    public static boolean validNonce(String nonce) {
        if (nonce == null || nonce.length() != 32) return false;
        for (int i = 0; i < 32; i++) {
            char c = nonce.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }
}
