package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.util.Collections;
import org.junit.Test;

public final class AccountBindingPresentationTest {
    @org.junit.Test public void incompleteInventoryLabelsOwnerAsLastConfirmed() {
        AccountInventory inventory = new AccountInventory(java.util.Collections.singletonMap(0,
                new AccountInventory.Owner(101, "Alice")), false, "pending");
        String row = AccountBindingPresentation.row(new CatalogEntry(DialogKey.of(0, 42), "Chat", 101),
                HiddenConfig.empty(), inventory);
        org.junit.Assert.assertTrue(row.contains("Last confirmed owner Alice"));
    }

    @Test public void collidingDialogShowsSavedAndCurrentOwnerInRowAndPrompt() {
        DialogKey key = DialogKey.of(0, 42);
        CatalogEntry replacementRow = new CatalogEntry(key, "Shared dialog", 202);
        HiddenConfig saved = HiddenConfig.fromBindings(Collections.singletonMap(key, 101L),
                Collections.emptySet(), false, false);
        AccountInventory current = new AccountInventory(Collections.singletonMap(0,
                new AccountInventory.Owner(202, "Bob")), true, "");
        String row = AccountBindingPresentation.row(replacementRow, saved, current);
        String prompt = AccountBindingPresentation.reviewPrompt(replacementRow, saved, current);
        assertTrue(row.contains("Saved owner 101"));
        assertTrue(row.contains("Current owner Bob (202)"));
        assertTrue(row.contains("review binding"));
        assertTrue(prompt.contains("Saved owner 101"));
        assertTrue(prompt.contains("Current owner Bob (202)"));
        assertTrue(prompt.contains("Bind"));
    }
}
