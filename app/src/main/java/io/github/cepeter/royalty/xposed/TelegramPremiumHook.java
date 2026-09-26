package io.github.cepeter.royalty.xposed;

import java.util.function.BooleanSupplier;

// Local Premium check ported from TeleVip-LSPosed TelePremium (GPL-3.0) by @mustafa1dev:
// forces client-side UserConfig.isPremium() to true so local-only Premium UI unlocks.
// Server-side entitlements (uploads, transcription, limits) are unaffected.
public final class TelegramPremiumHook {
    private TelegramPremiumHook() {}

    interface StatusReporter {
        void report(String status, String detail);
    }

    public static void install(
            ClassLoader classLoader, BooleanSupplier enabled, StatusReporter status) {
        Class<?> userConfig =
                ModernHookBridge.findClass("org.telegram.messenger.UserConfig", classLoader);
        if (ModernHookBridge.hookAllMethods(
                        userConfig, "isPremium", new ModernHookBridge.MethodHook() {
                            @Override
                            protected void beforeHookedMethod(
                                    ModernHookBridge.MethodHookParam param) {
                                try {
                                    if (enabled.getAsBoolean()) {
                                        param.setResult(Boolean.TRUE);
                                    }
                                } catch (Throwable error) {
                                    if (error instanceof VirtualMachineError) {
                                        throw (VirtualMachineError) error;
                                    }
                                    status.report("runtime_error", error.getClass().getSimpleName());
                                    ModernHookBridge.log(
                                            "Royalty: premium hook runtime error: " + error);
                                }
                            }
                        })
                .isEmpty()) {
            throw new IllegalStateException("UserConfig.isPremium not found");
        }
    }
}
