package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class BackupOperationTest {
    @Test public void queuedCancellationClearsPassphraseEvenIfBodyNeverStarts() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch running = new CountDownLatch(1), release = new CountDownLatch(1);
            Future<?> blocker = executor.submit(() -> {
                running.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(running.await(2, TimeUnit.SECONDS));
            char[] secret = "queued passphrase".toCharArray();
            BackupOperation operation = new BackupOperation(secret);
            Future<Boolean> queued = executor.submit(() -> {
                try { operation.checkActive(); return true; }
                catch (CancellationException expected) { return false; }
            });
            operation.cancel();
            for (char c : secret) assertEquals('\0', c);
            release.countDown(); blocker.get(2, TimeUnit.SECONDS);
            assertFalse(queued.get(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test public void runningCancellationClosesProviderStreamAndStopsFurtherWrites() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            char[] secret = "running passphrase".toCharArray();
            BackupOperation operation = new BackupOperation(secret);
            CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1);
            TrackedStream stream = new TrackedStream();
            Future<Boolean> work = executor.submit(() -> {
                try {
                    operation.track(stream);
                    opened.countDown();
                    release.await();
                    operation.checkActive();
                    stream.write(1);
                    return true;
                } catch (CancellationException expected) { return false; }
                finally { operation.clearSecret(); }
            });
            assertTrue(opened.await(2, TimeUnit.SECONDS));
            operation.cancel();
            assertTrue(stream.closed);
            for (char c : secret) assertEquals('\0', c);
            release.countDown();
            assertFalse(work.get(2, TimeUnit.SECONDS));
            assertEquals(0, stream.size());
        } finally { executor.shutdownNow(); }
    }
    private static final class TrackedStream extends ByteArrayOutputStream {
        volatile boolean closed;
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
