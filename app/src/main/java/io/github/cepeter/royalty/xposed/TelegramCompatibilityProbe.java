package io.github.cepeter.royalty.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TelegramCompatibilityProbe {
    private TelegramCompatibilityProbe() {}

    public static void verify(ClassLoader classLoader) throws ReflectiveOperationException {
        verify(name -> Class.forName(name, false, classLoader));
    }

    static void verify(ClassLookup classes) throws ReflectiveOperationException {
        Class<?> dialogsSearch = requireClass(classes, "we.b0");
        requireFields(dialogsSearch, "r0", "s", "F", "s0", "t0", "u0", "w0");
        requireMethod(dialogsSearch, "U", int.class, String.class);
        requireMethod(dialogsSearch, "J", int.class);
        requireMethod(dialogsSearch, "h");

        requireFields(requireClass(classes, "we.a0"), "a", "b");
        requireFields(
                requireClass(classes, "we.n1"),
                "m", "d", "e", "f", "g", "h", "i", "j", "k", "l");

        requireFields(
                requireClass(classes, "org.telegram.ui.Components.wq0"),
                "J", "K", "L", "T");
        requireFields(
                requireClass(classes, "org.telegram.ui.Components.oq0"),
                "d", "e");
        Class<?> shareSearch = requireClass(classes, "org.telegram.ui.Components.sq0");
        requireFields(shareSearch, "d", "e");
        requireMethod(shareSearch, "E", String.class);
        requireFields(
                requireClass(classes, "org.telegram.ui.Components.kq0"),
                "a", "b", "c", "d");

        requireFields(requireClass(classes, "org.telegram.ui.ContactsActivity"), "r", "d");
        requireFields(requireClass(classes, "org.telegram.ui.mt"), "d", "e", "f", "G");
        requireFields(requireClass(classes, "org.telegram.ui.nt"), "y");

        requireFields(requireClass(classes, "org.telegram.ui.s70"), "v");
        Class<?> groupAdapter = requireClass(classes, "org.telegram.ui.q70");
        requireFields(groupAdapter, "d", "e", "f", "r");
        requireMethod(groupAdapter, "L", String.class);
    }

    private static Class<?> requireClass(ClassLookup classes, String name)
            throws ClassNotFoundException {
        Class<?> type = classes.load(name);
        if (type == null) {
            throw new ClassNotFoundException(name);
        }
        return type;
    }

    private static void requireFields(Class<?> type, String... names)
            throws NoSuchFieldException {
        for (String name : names) {
            requireField(type, name);
        }
    }

    private static Field requireField(Class<?> type, String name)
            throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException missingField) {
                // Telegram adapter fields can live on an obfuscated superclass.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method requireMethod(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException missingMethod) {
                // Telegram adapter methods can live on an obfuscated superclass.
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    interface ClassLookup {
        Class<?> load(String name) throws ClassNotFoundException;
    }
}
