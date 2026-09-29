package io.github.cepeter.royalty.core;

/** Explicit component route: application ID changes in debug, Java class name does not. */
public final class AuthenticationRoute {
    private AuthenticationRoute() {}
    public static String packageName(String installedModulePackage) {
        if (installedModulePackage == null || installedModulePackage.length() == 0)
            throw new IllegalArgumentException("module package unavailable");
        return installedModulePackage;
    }
    public static String className() { return "io.github.cepeter.royalty.AuthenticationActivity"; }
}
