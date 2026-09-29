package io.github.cepeter.royalty.xposed;

/** One readiness decision for gesture hooks and Android reveal prerequisites. */
final class RevealInstallation {
    interface Reporter { void report(boolean installed, Throwable error); }
    static void install(ModernHookBridge.Installer gestures, ModernHookBridge.Installer adapters, Reporter reporter) {
        try { ModernHookBridge.installAtomically(() -> { gestures.install(); adapters.install(); }); reporter.report(true, null); }
        catch (Throwable failure) { reporter.report(false, failure); }
    }
}
