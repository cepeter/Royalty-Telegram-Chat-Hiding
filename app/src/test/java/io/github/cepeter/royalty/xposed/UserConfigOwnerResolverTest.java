package io.github.cepeter.royalty.xposed;

import static org.junit.Assert.*;
import org.junit.Test;
import org.telegram.messenger.UserConfig;
import io.github.cepeter.royalty.core.AccountInventory;

public final class UserConfigOwnerResolverTest {
    @Test public void readsEachSlotAndIncludesActiveEmptyOwner() {
        UserConfig.getInstance(0).id = 111;
        UserConfig.getInstance(1).id = 222;
        AccountInventory inventory = new UserConfigOwnerResolver(getClass().getClassLoader()).read();
        assertTrue(inventory.complete());
        assertEquals(111, inventory.owner(0).id());
        assertEquals(222, inventory.owner(1).id());
        UserConfig.getInstance(0).id = 0;
        UserConfig.getInstance(1).id = 0;
    }
    @Test public void unloadedSlotPreventsLogoutProof() {
        UserConfig.getInstance(0).loaded = false;
        AccountInventory inventory = new UserConfigOwnerResolver(getClass().getClassLoader()).read();
        assertFalse(inventory.complete());
        assertFalse(inventory.confirmedAbsent(0));
        UserConfig.getInstance(0).loaded = true;
    }
}
