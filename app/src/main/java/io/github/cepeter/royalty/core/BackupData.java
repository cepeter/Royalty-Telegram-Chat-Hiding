package io.github.cepeter.royalty.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Only saved, owner-bound selections and non-authentication preferences enter a backup. */
public final class BackupData {
    public static final int MAX_ENTRIES = 16 * 1024;
    public static final class Entry {
        private final long ownerId, dialogId;
        public Entry(long ownerId, long dialogId) {
            if (ownerId <= 0 || dialogId == 0) throw new IllegalArgumentException("invalid owner/dialog ID");
            this.ownerId = ownerId; this.dialogId = dialogId;
        }
        public long ownerId() { return ownerId; }
        public long dialogId() { return dialogId; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry e = (Entry) other; return ownerId == e.ownerId && dialogId == e.dialogId;
        }
        @Override public int hashCode() { return 31 * Long.hashCode(ownerId) + Long.hashCode(dialogId); }
    }
    private final List<Entry> entries;
    private final boolean notifications, premium, background, screenOff;
    private final int timeout;
    public BackupData(List<Entry> entries, boolean notifications, boolean premium,
            boolean background, boolean screenOff, int timeout) {
        if (entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many entries");
        newPrivacyCheck(timeout);
        Set<Entry> unique = new HashSet<>(entries);
        if (unique.size() != entries.size() || unique.contains(null)) throw new IllegalArgumentException("duplicate or null entry");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.notifications = notifications; this.premium = premium;
        this.background = background; this.screenOff = screenOff; this.timeout = timeout;
    }
    private static void newPrivacyCheck(int timeout) {
        if (timeout != 0 && timeout != 30000 && timeout != 60000 && timeout != 300000)
            throw new IllegalArgumentException("unsupported timeout");
    }
    public static BackupData fromSaved(HiddenConfig saved) {
        Objects.requireNonNull(saved, "saved");
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<DialogKey, Long> binding : saved.bindings().entrySet())
            entries.add(new Entry(binding.getValue(), binding.getKey().dialogId()));
        entries.sort(Comparator.comparingLong(Entry::ownerId).thenComparingLong(Entry::dialogId));
        return new BackupData(entries, saved.suppressNotifications(), saved.localPremium(),
                saved.concealOnBackground(), saved.concealOnScreenOff(), saved.revealTimeoutMs());
    }
    public List<Entry> entries() { return entries; }
    public boolean notifications() { return notifications; }
    public boolean premium() { return premium; }
    public boolean background() { return background; }
    public boolean screenOff() { return screenOff; }
    public int timeout() { return timeout; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof BackupData)) return false;
        BackupData d = (BackupData) other;
        return entries.equals(d.entries) && notifications == d.notifications && premium == d.premium
                && background == d.background && screenOff == d.screenOff && timeout == d.timeout;
    }
    @Override public int hashCode() { return Objects.hash(entries, notifications, premium, background, screenOff, timeout); }
}
