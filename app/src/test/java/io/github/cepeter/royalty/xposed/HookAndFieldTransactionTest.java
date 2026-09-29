package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class HookAndFieldTransactionTest {
    private static final class Handle implements XposedInterface.HookHandle {
        final String name;
        final List<String> removed;
        Handle(String name, List<String> removed) { this.name = name; this.removed = removed; }
        @Override public Executable getExecutable() { return null; }
        @Override public void unhook() { removed.add(name); }
    }

    @Test
    public void failedSurfaceUnhooksEveryPartialRegistrationInReverseOrder() {
        List<String> removed = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> ModernHookBridge.installAtomically(() -> {
            ModernHookBridge.trackHandle(new Handle("first", removed));
            ModernHookBridge.trackHandle(new Handle("second", removed));
            throw new IllegalStateException("third registration failed");
        }));
        assertEquals(Arrays.asList("second", "first"), removed);
    }

    @Test
    public void successfulSurfaceKeepsAllRegistrations() throws Throwable {
        List<String> removed = new ArrayList<>();
        ModernHookBridge.installAtomically(() ->
                ModernHookBridge.trackHandle(new Handle("first", removed)));
        assertTrue(removed.isEmpty());
    }

    private static final class Fields {
        List<String> items = new ArrayList<>(Arrays.asList("original"));
        List<String> names = new ArrayList<>(Arrays.asList("name"));
    }

    @Test
    public void secondWriteFailureRestoresFirstFieldAndOriginalReferences() {
        Fields owner = new Fields();
        List<String> originalItems = owner.items;
        List<String> originalNames = owner.names;
        assertThrows(IllegalStateException.class, () -> FieldWriteTransaction.writePair(
                owner, "items", new ArrayList<>(Arrays.asList("filtered")),
                "names", new ArrayList<>(Arrays.asList("filtered-name")),
                (target, field, value) -> {
                    if ("names".equals(field)) throw new IllegalStateException("write failed");
                    ModernHookBridge.setObjectField(target, field, value);
                }));
        assertSame(originalItems, owner.items);
        assertSame(originalNames, owner.names);
    }
}
