package io.github.cepeter.royalty.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TelegramCompatibilityProbe {
    private TelegramCompatibilityProbe() {}

    public static void verify(ClassLoader classLoader) throws ReflectiveOperationException {
        verify(name -> Class.forName(name, false, classLoader));
    }

    static void verify(TelegramSemanticResolver symbols) throws ReflectiveOperationException {
        Class<?> dialogsSearchView =
                symbols.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH_VIEW);
        symbols.resolveMethodByArity(
                "search.view.invalidate", dialogsSearchView, 0, "l");

        Class<?> dialogsSearch =
                symbols.resolveClass(TelegramSemanticResolver.Target.DIALOG_SEARCH);
        requireFields(dialogsSearch, "r0");
        symbols.resolveMethod("search.invalidate", dialogsSearch, null,
                new Class<?>[] {int.class, String.class}, "U");
        symbols.resolveMethod("search.item", dialogsSearch, null,
                new Class<?>[] {int.class}, "J");
        symbols.resolveMethod("search.count", dialogsSearch, int.class,
                new Class<?>[0], "h");

        Class<?> recent = symbols.resolveClass(TelegramSemanticResolver.Target.RECENT_SEARCH_ROW);
        requireFields(recent, "c");

        Class<?> helper = symbols.resolveClass(TelegramSemanticResolver.Target.SEARCH_HELPER);
        requireFields(helper, "d", "e", "g", "j", "k", "l");

        Class<?> shareAlert = symbols.resolveClass(TelegramSemanticResolver.Target.SHARE_ALERT);
        requireFields(shareAlert, "D0", "T");

        Class<?> shareList = symbols.resolveClass(TelegramSemanticResolver.Target.SHARE_LIST);
        requireFields(shareList, "d", "e", "f");

        Class<?> shareSearch = symbols.resolveClass(TelegramSemanticResolver.Target.SHARE_SEARCH);
        requireFields(shareSearch, "d", "e", "J");
        symbols.resolveMethod("share.search.query", shareSearch, null,
                new Class<?>[] {String.class}, "E");

        Class<?> shareRow = symbols.resolveClass(TelegramSemanticResolver.Target.SHARE_ROW);
        requireFields(shareRow, "a", "b");

        Class<?> contacts = symbols.resolveClass(TelegramSemanticResolver.Target.CONTACTS_ACTIVITY);
        requireFields(contacts, "r", "d");

        Class<?> contactSearch = symbols.resolveClass(TelegramSemanticResolver.Target.CONTACT_SEARCH);
        requireFields(contactSearch, "d", "e", "f", "G", "J");
        symbols.resolveMethodByArity("contacts.search.count", contactSearch, 0, "h");
        symbols.resolveMethod("contacts.search.query", contactSearch, null,
                new Class<?>[] {String.class}, "G");

        Class<?> contactList = symbols.resolveClass(TelegramSemanticResolver.Target.CONTACT_LIST);
        requireFields(contactList, "r");
        symbols.resolveMethod("contacts.sections.count", contactList, int.class,
                new Class<?>[] {int.class}, "M");
        symbols.resolveMethod("contacts.sections.item", contactList, null,
                new Class<?>[] {int.class, int.class}, "O");
        symbols.resolveMethodByArity("contacts.sections.refresh", contactList, 0, "l");

        symbols.resolveClass(TelegramSemanticResolver.Target.GROUP_ACTIVITY);
        Class<?> groupAdapter = symbols.resolveClass(TelegramSemanticResolver.Target.GROUP_ADAPTER);
        requireFields(groupAdapter, "d", "e", "f", "r", "H");
        symbols.resolveMethod("contacts.group.search", groupAdapter, null,
                new Class<?>[] {String.class}, "L");
        symbols.resolveMethodByArity("contacts.group.count", groupAdapter, 0, "h");
    }

    static void verify(ClassLookup classes) throws ReflectiveOperationException {
        Class<?> dialogsSearchView = requireClass(
                classes, "org.telegram.ui.Components.eo0");
        requireMethod(dialogsSearchView, "l");

        Class<?> dialogsSearch = requireClass(classes, "we.b0");
        requireFields(dialogsSearch, "r0", "s", "F", "s0", "t0", "u0", "w0");
        requireMethod(dialogsSearch, "U", int.class, String.class);
        requireMethod(dialogsSearch, "J", int.class);
        requireMethod(dialogsSearch, "h");
        requireMethod(dialogsSearch, "l");

        Class<?> recent = requireClass(classes, "we.a0");
        requireFields(recent, "a", "b", "c");
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
        Class<?> dialogMap = requireClass(classes, "z.f");
        requireMethod(dialogMap, "b");
        requireMethod(dialogMap, "k", Object.class, long.class);

        requireFields(requireClass(classes, "org.telegram.ui.ContactsActivity"), "r", "d");
        Class<?> contactSearch = requireClass(classes, "org.telegram.ui.mt");
        requireFields(contactSearch, "d", "e", "f", "G", "J");
        requireMethod(contactSearch, "h");
        requireMethod(contactSearch, "E", int.class);
        requireMethod(contactSearch, "G", String.class);

        Class<?> contactList = requireClass(classes, "org.telegram.ui.nt");
        requireFields(contactList, "y", "r");
        requireMethod(contactList, "M", int.class);
        requireMethod(contactList, "O", int.class, int.class);
        requireMethod(contactList, "N", int.class, int.class);
        requireMethod(contactList, "P", int.class, int.class);
        requireMethod(contactList, "l");

        requireFields(requireClass(classes, "org.telegram.ui.s70"), "v");
        Class<?> groupAdapter = requireClass(classes, "org.telegram.ui.q70");
        requireFields(groupAdapter, "d", "e", "f", "r", "H");
        requireMethod(groupAdapter, "L", String.class);
        requireMethod(groupAdapter, "h");
        requireMethod(groupAdapter, "j", int.class);
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
