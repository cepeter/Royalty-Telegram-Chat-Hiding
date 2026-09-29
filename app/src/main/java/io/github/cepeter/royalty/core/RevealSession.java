package io.github.cepeter.royalty.core;

import java.security.SecureRandom;

/** Sole owner of reveal state and one-use credential challenges. Time is monotonic milliseconds. */
public final class RevealSession {
    private final SecureRandom random = new SecureRandom();
    private HiddenConfig config = HiddenConfig.empty();
    private boolean revealed;
    private boolean foreground = true;
    private boolean credentialHandoff;
    private boolean pendingAuthorized;
    private long revealedAt;
    private long generation;
    private String nonce;
    private long challengeAt;

    public synchronized boolean revealed() { return revealed; }
    public synchronized long generation() { return generation; }
    public synchronized int timeoutMs() { return config.revealTimeoutMs(); }
    public synchronized boolean authenticationRequired() { return config.authenticate(); }
    public synchronized boolean credentialHandoff() { return credentialHandoff; }
    public synchronized void configure(HiddenConfig next) {
        if (!config.equals(next)) {
            config = next;
            nonce = null;
            pendingAuthorized = false;
            credentialHandoff = false;
            conceal();
        }
    }
    public synchronized boolean toggle(long now) {
        if (revealed) { conceal(); return false; }
        if (config.authenticate() || !foreground) return false;
        reveal(now);
        return true;
    }
    public synchronized String beginChallenge(long now) {
        if (!config.authenticate() || !foreground || revealed) return null;
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder out = new StringBuilder(32);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >>> 4) & 15, 16));
            out.append(Character.forDigit(value & 15, 16));
        }
        nonce = out.toString();
        challengeAt = now;
        credentialHandoff = true;
        pendingAuthorized = false;
        return nonce;
    }
    public synchronized void cancelChallenge() {
        nonce = null;
        pendingAuthorized = false;
        credentialHandoff = false;
    }
    public synchronized boolean authorize(String response, long now) {
        if (nonce == null || response == null || !nonce.equals(response)
                || now < challengeAt || now - challengeAt >= 120000 || !config.authenticate()) return false;
        nonce = null;
        if (!foreground) {
            if (!credentialHandoff) return false;
            pendingAuthorized = true;
            return false;
        }
        credentialHandoff = false;
        reveal(now);
        return true;
    }
    public synchronized void onForeground(long now) {
        onTime(now);
        foreground = true;
        credentialHandoff = false;
        if (pendingAuthorized) {
            pendingAuthorized = false;
            if (now >= challengeAt && now - challengeAt < 120000) reveal(now);
        }
    }
    public synchronized boolean onBackground(boolean handoff) {
        foreground = false;
        if (!handoff || !credentialHandoff) {
            nonce = null;
            pendingAuthorized = false;
            credentialHandoff = false;
        }
        if (config.concealOnBackground() && !(handoff && credentialHandoff)) return conceal();
        return false;
    }
    public synchronized boolean onScreenOff() {
        nonce = null;
        pendingAuthorized = false;
        credentialHandoff = false;
        return config.concealOnScreenOff() && conceal();
    }
    public synchronized boolean onTime(long now) {
        if (!revealed || config.revealTimeoutMs() == 0
                || now < revealedAt || now - revealedAt < config.revealTimeoutMs()) return false;
        return conceal();
    }
    public synchronized boolean onTimeout(long expectedGeneration, long now) {
        return expectedGeneration == generation && onTime(now);
    }
    private void reveal(long now) { revealed = true; revealedAt = now; generation++; }
    private boolean conceal() { boolean changed = revealed; revealed = false; generation++; return changed; }
}
