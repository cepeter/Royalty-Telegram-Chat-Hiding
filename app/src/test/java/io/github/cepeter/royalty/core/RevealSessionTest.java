package io.github.cepeter.royalty.core;

import static org.junit.Assert.*;
import org.junit.Test;

public final class RevealSessionTest {
    @Test public void overlapFinalOrdinaryStopCannotBeMaskedByRecreation() {
        ActivityVisibility visibility = new ActivityVisibility(); visibility.started("a"); visibility.started("b");
        assertFalse(visibility.stopped("a", true)); assertTrue(visibility.stopped("b", false));
    }

    @Test public void defaultsAllowManualRevealWithoutAutomaticConcealment() {
        RevealSession session = new RevealSession();
        assertTrue(session.toggle(100));
        session.onBackground(false);
        session.onScreenOff();
        assertTrue(session.revealed());
    }

    @Test public void enabledTriggersConcealAndStaleTimerCannotCloseNewReveal() {
        RevealSession session = new RevealSession();
        session.configure(HiddenConfig.empty().withPrivacy(true, true, 30000, false));
        assertTrue(session.toggle(100));
        long old = session.generation();
        assertFalse(session.onTimeout(old, 30099));
        assertFalse(session.toggle(30101));
        assertTrue(session.toggle(30102));
        assertFalse(session.onTimeout(old, 60103));
        assertTrue(session.revealed());
        assertTrue(session.onTimeout(session.generation(), 60102));
        assertFalse(session.revealed());
        assertTrue(session.toggle(70000));
        session.onBackground(false);
        assertFalse(session.revealed());
        session.onForeground(70001);
        assertTrue(session.toggle(80000));
        session.onScreenOff();
        assertFalse(session.revealed());
    }

    @Test public void challengeRequiresActiveNonceForegroundAndCurrentPolicy() {
        RevealSession session = new RevealSession();
        session.configure(HiddenConfig.empty().withPrivacy(true, false, 0, true));
        assertFalse(session.toggle(1));
        String first = session.beginChallenge(1);
        assertEquals(32, first.length());
        assertFalse(session.authorize("wrong", 2));
        assertFalse(session.authorize(first, 120001));
        String cancelled = session.beginChallenge(150000);
        session.cancelChallenge();
        assertFalse(session.authorize(cancelled, 150001));
        String second = session.beginChallenge(200000);
        assertFalse(session.authorize(first, 200001));
        session.onBackground(true);
        assertFalse(session.authorize(second, 200002));
        assertFalse(session.revealed());
        session.onForeground(200003);
        assertTrue(session.revealed());
        assertFalse(session.authorize(second, 200003));
        session.onBackground(false);
        assertFalse(session.revealed());
        String third = session.beginChallenge(300000);
        session.configure(HiddenConfig.empty().withPrivacy(true, false, 30000, true));
        assertFalse(session.authorize(third, 300001));
        String fourth = session.beginChallenge(400000);
        session.onScreenOff();
        assertFalse(session.authorize(fourth, 400001));
        session.onForeground(400002);
        String fifth = session.beginChallenge(500000);
        session.onBackground(true);
        assertFalse(session.authorize(fifth, 500001));
        session.onForeground(620000);
        assertFalse(session.revealed());
    }

    @Test public void elapsedSleepPastDeadlineConcealsOnForegroundAndQuery() {
        RevealSession session = new RevealSession();
        session.configure(HiddenConfig.empty().withPrivacy(false, false, 30000, false));
        assertTrue(session.toggle(100));
        session.onBackground(false);
        session.onForeground(60100);
        assertFalse(session.revealed());
        assertTrue(session.toggle(70000));
        assertTrue(session.onTime(100001));
        assertFalse(session.revealed());
    }

    @Test public void configurationRecreationDoesNotCountAsBackground() {
        ActivityVisibility visibility = new ActivityVisibility();
        visibility.started("activity");
        assertFalse(visibility.stopped("activity", true));
        visibility.started("activity");
        assertTrue(visibility.stopped("activity", false));
    }
}
