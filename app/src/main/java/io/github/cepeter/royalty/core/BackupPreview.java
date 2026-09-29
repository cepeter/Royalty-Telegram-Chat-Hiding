package io.github.cepeter.royalty.core;

import java.util.HashMap;
import java.util.Map;

/** Pure additive import plan; owner resolution is checked again at Apply. */
public final class BackupPreview {
    private final BackupData source;
    private final HiddenConfig before, merged;
    private final int added, already, skipped, ambiguous, conflicts;
    private final int recognizedOwners, skippedOwners;

    private BackupPreview(BackupData source, HiddenConfig before, HiddenConfig merged,
            int added, int already, int skipped, int ambiguous, int conflicts,
            int recognizedOwners, int skippedOwners) {
        this.source = source; this.before = before; this.merged = merged;
        this.added = added; this.already = already; this.skipped = skipped;
        this.ambiguous = ambiguous; this.conflicts = conflicts;
        this.recognizedOwners = recognizedOwners; this.skippedOwners = skippedOwners;
    }

    public static BackupPreview create(BackupData source, HiddenConfig before, AccountInventory inventory) {
        if (source == null || before == null) throw new IllegalArgumentException("missing preview input");
        Map<Long, Integer> slots = new HashMap<>();
        if (inventory != null && inventory.complete()) {
            for (Map.Entry<Integer, AccountInventory.Owner> owner : inventory.owners().entrySet()) {
                long id = owner.getValue().id();
                if (slots.containsKey(id)) slots.put(id, -1);
                else slots.put(id, owner.getKey());
            }
        }
        HiddenConfig result = before;
        int added = 0, already = 0, skipped = 0, ambiguous = 0, conflicts = 0;
        java.util.Set<Long> recognized = new java.util.HashSet<>(), missing = new java.util.HashSet<>();
        for (BackupData.Entry entry : source.entries()) {
            Integer slot = slots.get(entry.ownerId());
            if (slot == null) { skipped++; missing.add(entry.ownerId()); continue; }
            if (slot < 0) { ambiguous++; missing.add(entry.ownerId()); continue; }
            recognized.add(entry.ownerId());
            DialogKey key = DialogKey.of(slot, entry.dialogId());
            Long prior = result.boundOwner(key);
            if (prior != null && prior != entry.ownerId()) { conflicts++; continue; }
            if (result.isHidden(key)) {
                // Legacy unbound entries need an explicit review, never an implicit owner binding.
                if (prior == null) { conflicts++; continue; }
                already++; continue;
            }
            result = result.withSelection(key, true, entry.ownerId());
            added++;
        }
        result = result.withOptions(source.notifications(), source.premium())
                .withPrivacy(source.background(), source.screenOff(), source.timeout(), before.authenticate());
        return new BackupPreview(source, before, result, added, already, skipped, ambiguous, conflicts,
                recognized.size(), missing.size());
    }

    public boolean apply(ConfigurationDraft draft, AccountInventory currentInventory) {
        if (draft == null || !draft.initialized() || !before.equals(draft.current())) return false;
        BackupPreview fresh = create(source, draft.current(), currentInventory);
        if (added != fresh.added || already != fresh.already || skipped != fresh.skipped
                || ambiguous != fresh.ambiguous || conflicts != fresh.conflicts
                || !merged.equals(fresh.merged)) return false;
        return draft.applyImported(merged);
    }
    public HiddenConfig merged() { return merged; }
    public int added() { return added; }
    public int already() { return already; }
    public int skipped() { return skipped; }
    public int ambiguous() { return ambiguous; }
    public int conflicts() { return conflicts; }
    public int recognizedOwners() { return recognizedOwners; }
    public int skippedOwners() { return skippedOwners; }
    public boolean preferencesChange() {
        return before.suppressNotifications() != merged.suppressNotifications()
                || before.localPremium() != merged.localPremium()
                || before.concealOnBackground() != merged.concealOnBackground()
                || before.concealOnScreenOff() != merged.concealOnScreenOff()
                || before.revealTimeoutMs() != merged.revealTimeoutMs();
    }
    public String preferenceChanges() {
        StringBuilder changes = new StringBuilder();
        appendChange(changes, "Notification suppression", before.suppressNotifications(), merged.suppressNotifications());
        appendChange(changes, "Local Premium", before.localPremium(), merged.localPremium());
        appendChange(changes, "Background concealment", before.concealOnBackground(), merged.concealOnBackground());
        appendChange(changes, "Screen-off concealment", before.concealOnScreenOff(), merged.concealOnScreenOff());
        if (before.revealTimeoutMs() != merged.revealTimeoutMs())
            changes.append("\nReveal timeout: ").append(before.revealTimeoutMs() / 1000)
                    .append("s → ").append(merged.revealTimeoutMs() / 1000).append('s');
        return changes.length() == 0 ? "None" : changes.toString();
    }
    private static void appendChange(StringBuilder out, String label, boolean oldValue, boolean newValue) {
        if (oldValue != newValue) out.append('\n').append(label).append(": ")
                .append(oldValue ? "on" : "off").append(" → ").append(newValue ? "on" : "off");
    }
}
