package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Test;

public final class TelegramSearchDescriptorTest {
    private static final class Ambiguous {
        void v(int a, int b) {}
        void v(String a, String b) {}
    }

    @Test
    public void ambiguousPositionDescriptorRejectsSurfaceInsteadOfHookingArbitraryOverload()
            throws Exception {
        Method resolver = TelegramSearchHook.class.getDeclaredMethod(
                "findMethod", Class.class, String.class, int.class);
        resolver.setAccessible(true);
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> resolver.invoke(null, Ambiguous.class, "v", 2));
        assertTrue(error.getCause() instanceof NoSuchMethodException);
    }
}
