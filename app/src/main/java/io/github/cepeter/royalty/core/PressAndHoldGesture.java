package io.github.cepeter.royalty.core;

public final class PressAndHoldGesture {
    private final long minimumDurationMs;
    private long startedAtMs = Long.MIN_VALUE;

    public PressAndHoldGesture(long minimumDurationMs) {
        if (minimumDurationMs <= 0) {
            throw new IllegalArgumentException("minimumDurationMs must be positive");
        }
        this.minimumDurationMs = minimumDurationMs;
    }

    public synchronized void onDown(long eventTimeMs) {
        startedAtMs = eventTimeMs;
    }

    public synchronized boolean onDeadline(long eventTimeMs) {
        if (startedAtMs == Long.MIN_VALUE || eventTimeMs < startedAtMs) {
            startedAtMs = Long.MIN_VALUE;
            return false;
        }
        if (eventTimeMs - startedAtMs < minimumDurationMs) {
            return false;
        }
        startedAtMs = Long.MIN_VALUE;
        return true;
    }

    public synchronized void cancel() {
        startedAtMs = Long.MIN_VALUE;
    }
}
