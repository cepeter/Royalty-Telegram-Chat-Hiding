package io.github.cepeter.royalty.core;

import java.util.HashMap;
import java.util.Map;

/** Pure additive import plan; owner resolution is checked again at Apply. */
public final class BackupPreview {
    private final BackupData source;
    private final HiddenConfig before, merged;
    private final int added, already, skipped, ambiguous, conflicts;
    private final int recognizedOwners, skippedOwners;
    private final String accountDetails;
    private static final int MAX_DISPLAYED_OWNER_GROUPS = 20;
    private static final class OwnerGroup {
        final long owner; final Integer slot; final String label;
        int added, already, unknown, ambiguous, conflict, legacy;
        OwnerGroup(long owner, Integer slot, AccountInventory inventory) {
            this.owner = owner; this.slot = slot;
            this.label = slot != null && slot >= 0 ? inventory.owner(slot).label() : "";
        }
        String describe() {
            String identity = "Owner " + owner + (slot != null && slot >= 0 ? " · Account " + (slot + 1) + " · " + label : "");
            if (unknown > 0) return identity + ": " + unknown + " skipped — owner not currently confirmed";
            if (ambiguous > 0) return identity + ": " + ambiguous + " skipped — owner appears in multiple current slots";
            return identity + ": " + added + " added, " + already + " already selected, "
                    + conflict + " skipped (existing owner conflict), " + legacy + " skipped (unbound selection needs explicit review)";
        }
    }

    private BackupPreview(BackupData source, HiddenConfig before, HiddenConfig merged,
            int added, int already, int skipped, int ambiguous, int conflicts,
            int recognizedOwners, int skippedOwners, String accountDetails) {
        this.source = source; this.before = before; this.merged = merged;
        this.added = added; this.already = already; this.skipped = skipped;
        this.ambiguous = ambiguous; this.conflicts = conflicts;
        this.accountDetails = accountDetails;
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
        Map<Long, OwnerGroup> groups = new java.util.TreeMap<>();
        for (BackupData.Entry entry : source.entries()) {
            Integer slot = slots.get(entry.ownerId());
            OwnerGroup group = groups.computeIfAbsent(entry.ownerId(), owner -> new OwnerGroup(owner, slot, inventory));
            if (slot == null) { group.unknown++; skipped++; missing.add(entry.ownerId()); continue; }
            if (slot < 0) { group.ambiguous++; ambiguous++; missing.add(entry.ownerId()); continue; }
            recognized.add(entry.ownerId());
            DialogKey key = DialogKey.of(slot, entry.dialogId());
            Long prior = result.boundOwner(key);
            if (prior != null && prior != entry.ownerId()) { group.conflict++; conflicts++; continue; }
            if (result.isHidden(key)) {
                // Legacy unbound entries need an explicit review, never an implicit owner binding.
                if (prior == null) { group.legacy++; conflicts++; continue; }
                group.already++; already++; continue;
            }
            result = result.withSelection(key, true, entry.ownerId());
            group.added++; added++;
        }
        result = result.withOptions(source.notifications(), source.premium())
                .withPrivacy(source.background(), source.screenOff(), source.timeout(), before.authenticate());
        return new BackupPreview(source, before, result, added, already, skipped, ambiguous, conflicts,
                recognized.size(), missing.size(), describeGroups(groups));
    }

    public boolean apply(ConfigurationDraft draft, AccountInventory currentInventory) {
        if (draft == null || !draft.initialized() || !before.equals(draft.current())) return false;
        BackupPreview fresh = create(source, draft.current(), currentInventory);
        if (added != fresh.added || already != fresh.already || skipped != fresh.skipped
                || ambiguous != fresh.ambiguous || conflicts != fresh.conflicts
                || !merged.equals(fresh.merged)) return false;
        return draft.applyImported(merged);
    }
    private static String describeGroups(Map<Long, OwnerGroup> groups) {
        java.util.List<OwnerGroup> ordered = new java.util.ArrayList<>(groups.values());
        ordered.sort(java.util.Comparator.comparingInt((OwnerGroup g) -> g.slot != null && g.slot >= 0 ? 0 : 1)
                .thenComparingLong(g -> g.owner));
        StringBuilder out = new StringBuilder();
        int omittedUnknown = 0, omittedAmbiguous = 0;
        for (int i = 0; i < ordered.size(); i++) {
            OwnerGroup group = ordered.get(i);
            if (i < MAX_DISPLAYED_OWNER_GROUPS) out.append('\n').append(group.describe());
            else { omittedUnknown += group.unknown; omittedAmbiguous += group.ambiguous; }
        }
        if (ordered.size() > MAX_DISPLAYED_OWNER_GROUPS)
            out.append('\n').append(ordered.size() - MAX_DISPLAYED_OWNER_GROUPS)
                    .append(" more owner groups: ").append(omittedUnknown)
                    .append(" selections skipped (owner not currently confirmed), ").append(omittedAmbiguous)
                    .append(" skipped (multiple current slots).");
        return out.length() == 0 ? "No selections in backup" : out.toString();
    }
    public String accountDetails() { return accountDetails; }
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
