package io.github.cepeter.royalty.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.cepeter.royalty.BuildConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;

/**
 * Resolves Telegram's obfuscated hook hosts from a validated cache, known aliases, then a
 * DexKit source/shape scan. Ambiguous semantic matches fail closed instead of hooking an
 * arbitrary class or method.
 */
final class TelegramSemanticResolver implements AutoCloseable {
    private static final int CACHE_SCHEMA = 1;
    private static final String CACHE_NAME = "io.github.cepeter.royalty.semantic_hooks";

    enum Target {
        DIALOG_SEARCH("dialog_search", "DialogsSearchAdapter.java", 3, "we.b0"),
        DIALOG_SEARCH_VIEW("dialog_search_view", "SearchViewPager.java", 1,
                "org.telegram.ui.Components.eo0"),
        RECENT_SEARCH_ROW("recent_search_row", "DialogsSearchAdapter.java", 3, "we.a0"),
        SEARCH_HELPER("search_helper", "SearchAdapterHelper.java", 2, "we.n1"),
        SHARE_ALERT("share_alert", "ShareAlert.java", 2, "org.telegram.ui.Components.wq0"),
        SHARE_LIST("share_list", "ShareAlert.java", 2, "org.telegram.ui.Components.oq0"),
        SHARE_SEARCH("share_search", "ShareAlert.java", 3, "org.telegram.ui.Components.sq0"),
        SHARE_ROW("share_row", "ShareAlert.java", 3, "org.telegram.ui.Components.kq0"),
        CONTACTS_ACTIVITY("contacts_activity", "ContactsActivity.java", 1,
                "org.telegram.ui.ContactsActivity"),
        CONTACT_SEARCH("contact_search", "ContactsActivity.java", 3, "org.telegram.ui.mt"),
        CONTACT_LIST("contact_list", "ContactsActivity.java", 3, "org.telegram.ui.nt"),
        GROUP_ACTIVITY("group_activity", "GroupCreateActivity.java", 1, "org.telegram.ui.s70"),
        GROUP_ADAPTER("group_adapter", "GroupCreateActivity.java", 3, "org.telegram.ui.q70");

        final String key;
        final String sourceFile;
        final int minimumScore;
        final String[] aliases;

        Target(String key, String sourceFile, int minimumScore, String... aliases) {
            this.key = key;
            this.sourceFile = sourceFile;
            this.minimumScore = minimumScore;
            this.aliases = aliases;
        }
    }

    interface Cache {
        String get(String key);
        void put(String key, String value);
        void remove(String key);
    }

    interface Discovery {
        List<String> discover(Target target) throws Exception;
    }

    private final ClassLoader loader;
    private final Cache cache;
    private final Discovery discovery;

    TelegramSemanticResolver(ClassLoader loader, Cache cache, Discovery discovery) {
        this.loader = loader;
        this.cache = cache;
        this.discovery = discovery;
    }

    static TelegramSemanticResolver create(
            Context context, ClassLoader loader, TelegramVersionGuard.Version version) {
        SharedPreferences preferences = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE);
        String namespace = "schema" + CACHE_SCHEMA + ".telegram" + version.code
                + ".module" + BuildConfig.VERSION_CODE + ".";
        return new TelegramSemanticResolver(
                loader,
                new SharedPreferencesCache(preferences, namespace),
                new DexKitDiscovery(context.getApplicationInfo().sourceDir));
    }

    Class<?> resolveClass(Target target) {
        String cacheKey = "class." + target.key;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            Class<?> type = load(cached);
            if (type != null && semanticScore(target, type) >= target.minimumScore) {
                return type;
            }
            cache.remove(cacheKey);
        }

        for (String alias : target.aliases) {
            Class<?> type = load(alias);
            if (type != null && semanticScore(target, type) >= target.minimumScore) {
                cache.put(cacheKey, type.getName());
                return type;
            }
        }

        List<String> discovered;
        try {
            discovered = discovery.discover(target);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "semantic discovery failed for " + target.key, error);
        }

        Class<?> best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean tied = false;
        Set<String> unique = new LinkedHashSet<>(discovered);
        for (String name : unique) {
            Class<?> candidate = load(name);
            if (candidate == null) continue;
            int score = semanticScore(target, candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
                tied = false;
            } else if (score == bestScore && score >= target.minimumScore) {
                tied = true;
            }
        }
        if (best == null || bestScore < target.minimumScore || tied) {
            throw new IllegalStateException(
                    "no unique semantic class for " + target.key + " (score " + bestScore + ")");
        }
        cache.put(cacheKey, best.getName());
        return best;
    }

    Method resolveMethod(String logicalKey, Class<?> type, Class<?> returnType,
            Class<?>[] parameterTypes, String... aliases) {
        String cacheKey = "method." + logicalKey;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            Method method = findNamed(type, cached, returnType, parameterTypes);
            if (method != null) return method;
            cache.remove(cacheKey);
        }
        for (String alias : aliases) {
            Method method = findNamed(type, alias, returnType, parameterTypes);
            if (method != null) {
                cache.put(cacheKey, method.getName());
                return method;
            }
        }
        Method resolved = uniqueBySignature(type, returnType, parameterTypes);
        if (resolved == null) {
            throw new IllegalStateException("ambiguous semantic method " + logicalKey
                    + " on " + type.getName());
        }
        cache.put(cacheKey, resolved.getName());
        return resolved;
    }

    Method resolveMethodByArity(
            String logicalKey, Class<?> type, int parameterCount, String... aliases) {
        String cacheKey = "method." + logicalKey;
        String cached = cache.get(cacheKey);
        if (cached != null) {
            Method method = findNamedByArity(type, cached, parameterCount);
            if (method != null) return method;
            cache.remove(cacheKey);
        }
        for (String alias : aliases) {
            Method method = findNamedByArity(type, alias, parameterCount);
            if (method != null) {
                cache.put(cacheKey, method.getName());
                return method;
            }
        }
        Method resolved = uniqueByArity(type, parameterCount);
        if (resolved == null) {
            throw new IllegalStateException("ambiguous semantic method " + logicalKey
                    + " on " + type.getName());
        }
        cache.put(cacheKey, resolved.getName());
        return resolved;
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findNamed(Class<?> type, String name, Class<?> returnType,
            Class<?>[] parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                if (!usable(method, returnType)) return null;
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Continue through obfuscated adapter superclasses.
            }
        }
        return null;
    }

    private static Method findNamedByArity(Class<?> type, String name, int parameterCount) {
        Method found = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != parameterCount
                        || method.isSynthetic() || method.isBridge()) continue;
                if (found != null) return null;
                found = method;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static Method uniqueBySignature(
            Class<?> type, Class<?> returnType, Class<?>[] parameterTypes) {
        Method found = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()
                        || !Arrays.equals(method.getParameterTypes(), parameterTypes)
                        || !usable(method, returnType)) continue;
                if (found != null && !sameOverride(found, method)) return null;
                if (found == null) found = method;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static Method uniqueByArity(Class<?> type, int parameterCount) {
        Method found = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()
                        || method.getParameterCount() != parameterCount) continue;
                if (found != null && !sameOverride(found, method)) return null;
                if (found == null) found = method;
            }
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    private static boolean sameOverride(Method first, Method second) {
        return first.getName().equals(second.getName())
                && first.getReturnType() == second.getReturnType()
                && Arrays.equals(first.getParameterTypes(), second.getParameterTypes());
    }

    private static boolean usable(Method method, Class<?> returnType) {
        return !method.isSynthetic() && !method.isBridge()
                && (returnType == null || method.getReturnType() == returnType);
    }

    private static int semanticScore(Target target, Class<?> type) {
        int lists = countFieldsAssignableTo(type, java.util.List.class);
        int ints = countFieldsOfType(type, int.class);
        int longs = countFieldsOfType(type, long.class);
        int fields = countInstanceFields(type);
        switch (target) {
            case DIALOG_SEARCH:
                return (lists >= 4 ? 1 : 0) + (ints >= 1 ? 1 : 0)
                        + (hasMethodShape(type, int.class, 0) ? 1 : 0)
                        + (hasMethodShape(type, null, 1, int.class) ? 1 : 0)
                        + (hasMethodShape(type, null, 2, int.class, String.class) ? 1 : 0);
            case DIALOG_SEARCH_VIEW:
                return hasMethodShape(type, null, 0) ? 1 : 0;
            case RECENT_SEARCH_ROW:
                return (fields >= 3 ? 1 : 0) + (ints >= 1 ? 1 : 0) + (longs >= 1 ? 1 : 0);
            case SEARCH_HELPER:
                return (lists >= 5 ? 1 : 0) + (ints >= 1 ? 1 : 0);
            case SHARE_ALERT:
                return fields >= 4 ? 2 : 0;
            case SHARE_LIST:
                return (lists >= 1 ? 1 : 0) + (fields >= 2 ? 1 : 0);
            case SHARE_SEARCH:
                return (lists >= 1 ? 1 : 0)
                        + (hasMethodShape(type, null, 1, String.class) ? 1 : 0)
                        + (fields >= 2 ? 1 : 0);
            case SHARE_ROW:
                return (fields >= 4 ? 1 : 0) + (ints >= 1 ? 1 : 0)
                        + (countFieldsAssignableTo(type, CharSequence.class) >= 1 ? 1 : 0);
            case CONTACTS_ACTIVITY:
                return fields >= 2 ? 1 : 0;
            case CONTACT_SEARCH:
                return (lists >= 3 ? 1 : 0)
                        + (hasMethodShape(type, null, 1, String.class) ? 1 : 0)
                        + (hasMethodShape(type, null, 1, int.class) ? 1 : 0);
            case CONTACT_LIST:
                return (lists >= 1 ? 1 : 0) + (ints >= 1 ? 1 : 0)
                        + (hasMethodShape(type, null, 2, int.class, int.class) ? 1 : 0);
            case GROUP_ACTIVITY:
                return fields >= 1 ? 1 : 0;
            case GROUP_ADAPTER:
                return (lists >= 3 ? 1 : 0)
                        + (hasMethodShape(type, null, 1, String.class) ? 1 : 0)
                        + (hasMethodShape(type, int.class, 0) ? 1 : 0);
            default:
                return 0;
        }
    }

    private static int countInstanceFields(Class<?> type) {
        int count = 0;
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) count++;
            }
        }
        return count;
    }

    private static int countFieldsOfType(Class<?> type, Class<?> expected) {
        int count = 0;
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == expected) count++;
            }
        }
        return count;
    }

    private static int countFieldsAssignableTo(Class<?> type, Class<?> expected) {
        int count = 0;
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())
                        && expected.isAssignableFrom(field.getType())) count++;
            }
        }
        return count;
    }

    private static boolean hasMethodShape(
            Class<?> type, Class<?> returnType, int parameterCount, Class<?>... exactParameters) {
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()
                        || method.getParameterCount() != parameterCount
                        || (returnType != null && method.getReturnType() != returnType)) continue;
                if (exactParameters.length == parameterCount
                        && !Arrays.equals(method.getParameterTypes(), exactParameters)) continue;
                return true;
            }
        }
        return false;
    }

    @Override public void close() {
        if (discovery instanceof AutoCloseable) {
            try {
                ((AutoCloseable) discovery).close();
            } catch (Exception ignored) {
                // Resolver cache is already durable; native scanner cleanup is best effort.
            }
        }
    }

    private static final class SharedPreferencesCache implements Cache {
        private final SharedPreferences preferences;
        private final String namespace;
        SharedPreferencesCache(SharedPreferences preferences, String namespace) {
            this.preferences = preferences;
            this.namespace = namespace;
        }
        @Override public String get(String key) {
            return preferences.getString(namespace + key, null);
        }
        @Override public void put(String key, String value) {
            preferences.edit().putString(namespace + key, value).apply();
        }
        @Override public void remove(String key) {
            preferences.edit().remove(namespace + key).apply();
        }
    }

    private static final class DexKitDiscovery implements Discovery, AutoCloseable {
        private final String apkPath;
        private DexKitBridge bridge;
        DexKitDiscovery(String apkPath) { this.apkPath = apkPath; }

        @Override public List<String> discover(Target target) {
            DexKitBridge scanner = scanner();
            LinkedHashSet<String> names = new LinkedHashSet<>();
            String[] fingerprints = fingerprints(target);
            if (fingerprints.length > 0) {
                for (ClassData data : scanner.findClass(
                        FindClass.create()
                                .searchPackages("org.telegram", "we", "z")
                                .matcher(ClassMatcher.create().usingStrings(fingerprints)))) {
                    names.add(data.getName());
                }
            }
            if (names.isEmpty()) {
                for (ClassData data : scanner.findClass(
                        FindClass.create()
                                .searchPackages("org.telegram", "we", "z")
                                .matcher(ClassMatcher.create().source(target.sourceFile)))) {
                    names.add(data.getName());
                }
            }
            return new ArrayList<>(names);
        }

        private static String[] fingerprints(Target target) {
            switch (target) {
                case DIALOG_SEARCH:
                    return new String[] {"SELECT did, date FROM search_recent WHERE 1"};
                case SEARCH_HELPER:
                    return new String[] {"SELECT id, date FROM hashtag_recent_v2 WHERE 1"};
                case SHARE_SEARCH:
                    return new String[] {
                        "SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400"
                    };
                default:
                    return new String[0];
            }
        }

        private synchronized DexKitBridge scanner() {
            if (bridge == null) bridge = DexKitBridge.create(apkPath);
            return bridge;
        }

        @Override public synchronized void close() {
            if (bridge != null) {
                bridge.close();
                bridge = null;
            }
        }
    }
}
