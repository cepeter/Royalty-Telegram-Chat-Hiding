package io.github.cepeter.royalty.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Owns one mutable secret and cancels queued or running backup work. */
public final class BackupOperation {
    private final char[] passphrase;
    private boolean cancelled;
    private Closeable resource;

    public BackupOperation(char[] passphrase) {
        if (passphrase == null) throw new IllegalArgumentException("missing passphrase");
        this.passphrase = passphrase;
    }
    public synchronized char[] passphrase() {
        checkActive();
        return passphrase;
    }
    public synchronized void checkActive() {
        if (cancelled) throw new CancellationException("backup operation cancelled");
    }
    public synchronized boolean cancelled() { return cancelled; }

    public void track(Closeable opened) {
        synchronized (this) {
            if (!cancelled) { resource = opened; return; }
        }
        closeQuietly(opened);
        throw new CancellationException("backup operation cancelled");
    }
    public synchronized void untrack(Closeable opened) {
        if (resource == opened) resource = null;
    }
    public void cancel() {
        Closeable opened;
        synchronized (this) {
            cancelled = true;
            Arrays.fill(passphrase, '\0');
            opened = resource;
            resource = null;
        }
        if (opened != null) {
            // Dedicated cancellation path: the I/O worker may need close to unblock.
            Thread closer = new Thread(() -> closeQuietly(opened), "royalty-backup-close");
            closer.setDaemon(true);
            closer.start();
        }
    }
    public synchronized void clearSecret() { Arrays.fill(passphrase, '\0'); }
    private static void closeQuietly(Closeable opened) {
        if (opened != null) try { opened.close(); } catch (IOException ignored) { }
    }
}
