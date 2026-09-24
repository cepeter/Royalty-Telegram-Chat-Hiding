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

    public synchronized boolean onUp(long eventTimeMs) {
        if (startedAtMs == Long.MIN_VALUE || eventTimeMs < startedAtMs) {
            startedAtMs = Long.MIN_VALUE;
            return false;
        }
        long durationMs = eventTimeMs - startedAtMs;
        startedAtMs = Long.MIN_VALUE;
        return durationMs >= minimumDurationMs;
    }

    public synchronized void cancel() {
        startedAtMs = Long.MIN_VALUE;
    }
}
