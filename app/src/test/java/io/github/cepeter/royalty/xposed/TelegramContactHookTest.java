package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.Test;

public final class TelegramContactHookTest {
    private abstract static class AbstractSections<T> {
        abstract T O(int section, int row);
        int P(int section, int row) { return 0; }
        static int N(int section, int row) { return 0; }
    }

    private static final class ConcreteSections extends AbstractSections<String> {
        @Override String O(int section, int row) { return "item"; }
    }

    @Test public void sectionPositionMethodsKeepOnlyExecutableInstanceDeclarations() {
        List<Method> methods = TelegramContactHook.sectionPositionMethods(ConcreteSections.class);

        assertEquals(2, methods.size());
        assertTrue(methods.stream().noneMatch(method -> Modifier.isAbstract(method.getModifiers())));
        assertTrue(methods.stream().noneMatch(method -> Modifier.isStatic(method.getModifiers())));
        assertTrue(methods.stream().noneMatch(Method::isSynthetic));
        assertTrue(methods.stream().noneMatch(Method::isBridge));
        assertTrue(methods.stream().anyMatch(method -> method.getName().equals("O")
                && method.getDeclaringClass() == ConcreteSections.class));
        assertTrue(methods.stream().anyMatch(method -> method.getName().equals("P")
                && method.getDeclaringClass() == AbstractSections.class));
    }
}
