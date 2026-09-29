package io.github.cepeter.royalty.core;

/** Owner context shared by catalog rows and explicit rebind confirmation. */
public final class AccountBindingPresentation {
    private AccountBindingPresentation() {}

    public static String row(CatalogEntry entry, HiddenConfig config, AccountInventory inventory) {
        DialogKey key = entry.key();
        StringBuilder text = new StringBuilder(entry.title()).append("\nAccount ")
                .append(key.account() + 1).append(" · ID ").append(key.dialogId());
        if (config.isHidden(key)) text.append(" · ").append(savedOwner(config, key));
        text.append(" · ").append(currentOwner(inventory, key.account()));
        if (entry.ownerId() > 0 && !inventory.matches(key.account(), entry.ownerId()))
            text.append(" · Catalog row owner ").append(entry.ownerId());
        if (needsReview(key, config, inventory)) text.append(" · review binding");
        return text.toString();
    }

    public static String reviewPrompt(CatalogEntry entry, HiddenConfig config, AccountInventory inventory) {
        DialogKey key = entry.key();
        String context = savedOwner(config, key) + ". " + currentOwner(inventory, key.account())
                + ". Catalog row owner " + (entry.ownerId() > 0 ? entry.ownerId() : "unavailable") + ".";
        if (inventory.complete() && entry.ownerId() > 0
                && inventory.matches(key.account(), entry.ownerId()))
            return context + " Bind this saved selection to the current owner?";
        return context + " Refresh after opening Telegram, or remove this selection.";
    }

    private static boolean needsReview(DialogKey key, HiddenConfig config, AccountInventory inventory) {
        Long saved = config.boundOwner(key);
        return config.isHidden(key) && (saved == null || !inventory.matches(key.account(), saved));
    }
    private static String savedOwner(HiddenConfig config, DialogKey key) {
        Long saved = config.boundOwner(key);
        return saved == null ? "Saved owner unbound" : "Saved owner " + saved;
    }
    private static String currentOwner(AccountInventory inventory, int slot) {
        AccountInventory.Owner owner = inventory.owner(slot);
        return owner == null ? "Current owner unconfirmed"
                : "Current owner " + owner.label() + " (" + owner.id() + ")";
    }
}
