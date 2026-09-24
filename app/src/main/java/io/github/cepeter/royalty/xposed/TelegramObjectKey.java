package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.DialogKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class TelegramObjectKey {
    private TelegramObjectKey() {}

    public static Optional<DialogKey> fromDialog(int account, Object dialog) {
        return fromSignedId(account, readLongField(dialog, "id"));
    }

    public static Optional<DialogKey> fromMessage(int account, Object message) {
        return fromSignedId(account, callLongMethod(message, "getDialogId"));
    }

    public static Optional<DialogKey> fromUser(int account, Object user) {
        Long id = readLongField(user, "id");
        return id != null && id > 0 ? fromSignedId(account, id) : Optional.empty();
    }

    public static Optional<DialogKey> fromChat(int account, Object chat) {
        Long id = readLongField(chat, "id");
        if (id == null || id <= 0 || id == Long.MIN_VALUE) {
            return Optional.empty();
        }
        return fromSignedId(account, -id);
    }

    public static Optional<DialogKey> fromRecent(int account, Object recent) {
        return fromSignedId(account, readLongField(recent, "did", "c", "a"));
    }

    public static Optional<DialogKey> fromSearchResult(int account, Object result) {
        if (hasTypeInHierarchy(result, "MessageObject")
                || hasNoArgMethod(result, "getDialogId")) {
            return fromMessage(account, result);
        }
        if (hasTypeInHierarchy(result, "Dialog")) {
            return fromDialog(account, result);
        }
        if (hasTypeInHierarchy(result, "User")) {
            return fromUser(account, result);
        }
        if (hasTypeInHierarchy(result, "Chat")) {
            return fromChat(account, result);
        }
        if (hasClassName(result, "we.a0") || hasSimpleNameContaining(result, "Recent")) {
            return fromRecent(account, result);
        }
        return Optional.empty();
    }

    public static Optional<DialogKey> fromContact(int account, Object contact) {
        Long id = readLongField(contact, "user_id", "userId");
        return id != null && id > 0 ? fromSignedId(account, id) : Optional.empty();
    }

    public static Optional<DialogKey> fromPickerResult(int account, Object result) {
        Optional<DialogKey> key = fromSearchResult(account, result);
        return key.isPresent() ? key : fromContact(account, result);
    }

    public static Optional<DialogKey> fromShareSearch(int account, Object result) {
        Object dialog = readObjectField(result, "dialog", "a");
        Optional<DialogKey> dialogKey = fromDialog(account, dialog);
        if (dialogKey.isPresent()) {
            return dialogKey;
        }

        Object peer = readObjectField(result, "object", "b");
        if (hasTypeInHierarchy(peer, "User")) {
            return fromUser(account, peer);
        }
        if (hasTypeInHierarchy(peer, "Chat")) {
            return fromChat(account, peer);
        }
        return Optional.empty();
    }

    private static Optional<DialogKey> fromSignedId(int account, Long dialogId) {
        if (dialogId == null || dialogId == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(DialogKey.of(account, dialogId));
        } catch (IllegalArgumentException invalidKey) {
            return Optional.empty();
        }
    }

    private static Long readLongField(Object target, String... names) {
        for (String name : names) {
            Object value = readObjectField(target, name);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        return null;
    }

    private static Object readObjectField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException missingField) {
                    // Try the same field name on the superclass.
                } catch (ReflectiveOperationException | RuntimeException unreadableField) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Long callLongMethod(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(target);
                return value instanceof Number ? ((Number) value).longValue() : null;
            } catch (NoSuchMethodException missingMethod) {
                // Try the same method name on the superclass.
            } catch (ReflectiveOperationException | RuntimeException unreadableMethod) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasNoArgMethod(Object target, String name) {
        if (target == null) {
            return false;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod(name);
                return true;
            } catch (NoSuchMethodException missingMethod) {
                // Try the same method name on the superclass.
            } catch (RuntimeException unreadableMethod) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasClassName(Object target, String className) {
        return target != null && className.equals(target.getClass().getName());
    }

    private static boolean hasSimpleNameContaining(Object target, String token) {
        if (target == null) {
            return false;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getSimpleName().contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTypeInHierarchy(Object target, String simpleName) {
        if (target == null) {
            return false;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            if (simpleName.equals(type.getSimpleName())) {
                return true;
            }
        }
        return false;
    }
}
