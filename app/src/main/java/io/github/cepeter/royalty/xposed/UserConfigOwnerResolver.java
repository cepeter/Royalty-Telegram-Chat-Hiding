package io.github.cepeter.royalty.xposed;

import io.github.cepeter.royalty.core.AccountInventory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/** Validates and caches only the retained per-account UserConfig API on the gated build. */
final class UserConfigOwnerResolver {
    private final Method getInstance;
    private final Method configLoaded;
    private final Method activated;
    private final Method userId;
    private final Method currentUser;
    private final int count;
    UserConfigOwnerResolver(ClassLoader loader) {
        try {
            Class<?> type = Class.forName("org.telegram.messenger.UserConfig", false, loader);
            Field maximum = type.getDeclaredField("MAX_ACCOUNT_COUNT");
            if (!Modifier.isStatic(maximum.getModifiers()) || maximum.getType() != int.class)
                throw new IllegalStateException("invalid MAX_ACCOUNT_COUNT");
            count = maximum.getInt(null);
            if (count != AccountInventory.MAX_ACCOUNTS) throw new IllegalStateException("unsupported account count");
            getInstance = type.getDeclaredMethod("getInstance", int.class);
            configLoaded = type.getDeclaredMethod("isConfigLoaded");
            activated = type.getDeclaredMethod("isClientActivated");
            userId = type.getDeclaredMethod("getClientUserId");
            currentUser = type.getDeclaredMethod("getCurrentUser");
            if (!Modifier.isStatic(getInstance.getModifiers()) || getInstance.getReturnType() != type
                    || configLoaded.getReturnType() != boolean.class
                    || activated.getReturnType() != boolean.class
                    || userId.getReturnType() != long.class
                    || currentUser.getReturnType() == void.class)
                throw new IllegalStateException("invalid UserConfig signatures");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("UserConfig API unavailable", error);
        }
    }
    AccountInventory read() {
        Map<Integer, AccountInventory.Owner> owners = new HashMap<>();
        try {
            for (int slot = 0; slot < count; slot++) {
                Object config = getInstance.invoke(null, slot);
                if (config == null || !(Boolean) configLoaded.invoke(config))
                    return new AccountInventory(owners, false, "account configuration not loaded");
                boolean active = (Boolean) activated.invoke(config);
                long id = (Long) userId.invoke(config);
                Object user = currentUser.invoke(config);
                if (active) {
                    if (id <= 0 || user == null) return new AccountInventory(owners, false, "active owner unavailable");
                    owners.put(slot, new AccountInventory.Owner(id, "Account " + (slot + 1) + " · " + id));
                } else if (id != 0 || user != null) {
                    return new AccountInventory(owners, false, "inconsistent inactive account");
                }
            }
            return new AccountInventory(owners, true, "");
        } catch (ReflectiveOperationException | RuntimeException error) {
            return new AccountInventory(owners, false, "owner lookup: " + error.getClass().getSimpleName());
        }
    }
}
