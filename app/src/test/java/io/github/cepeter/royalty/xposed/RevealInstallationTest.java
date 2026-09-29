package io.github.cepeter.royalty.xposed;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
public final class RevealInstallationTest {
    @Test public void adapterFailureCannotBeOverwrittenByGestureSuccess() {
        List<Boolean> states = new ArrayList<>();
        RevealInstallation.install(() -> {}, () -> { throw new IllegalStateException("screen registration"); },
                (installed, error) -> states.add(installed));
        assertEquals(Collections.singletonList(false), states);
    }
    @Test public void everyPartialAdapterIsRolledBackAndRemainsInactiveEvenIfCleanupThrows() {
        List<String> removed = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<java.util.function.BooleanSupplier> active = new java.util.concurrent.atomic.AtomicReference<>();
        List<Boolean> states = new ArrayList<>();
        RevealInstallation.install(() -> ModernHookBridge.trackCleanup(() -> removed.add("gesture")), () -> {
            active.set(ModernHookBridge.installationActive());
            assertFalse(active.get().getAsBoolean());
            ModernHookBridge.trackCleanup(() -> removed.add("lifecycle"));
            ModernHookBridge.trackCleanup(() -> { removed.add("receivers"); throw new IllegalStateException("unregister failed"); });
            throw new IllegalStateException("auth registration failed");
        }, (installed, error) -> { states.add(installed); assertEquals(1, error.getSuppressed().length); });
        assertEquals(Collections.singletonList(false), states);
        assertEquals(Arrays.asList("receivers", "lifecycle", "gesture"), removed);
        assertFalse(active.get().getAsBoolean());
    }
    @Test public void gestureFailureNeverRegistersAdaptersAndCompleteSuccessActivatesAll() {
        List<Boolean> states = new ArrayList<>();
        RevealInstallation.install(() -> { throw new IllegalStateException("gesture"); },
                () -> fail("adapters installed after failed gesture"), (installed, error) -> states.add(installed));
        java.util.concurrent.atomic.AtomicReference<java.util.function.BooleanSupplier> active = new java.util.concurrent.atomic.AtomicReference<>();
        RevealInstallation.install(() -> {}, () -> active.set(ModernHookBridge.installationActive()),
                (installed, error) -> states.add(installed));
        assertEquals(Arrays.asList(false, true), states); assertTrue(active.get().getAsBoolean());
    }
}
