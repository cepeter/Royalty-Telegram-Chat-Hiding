package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PressAndHoldGestureTest {
    @Test
    public void triggersAtThreeSecondDeadline() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);

        gesture.onDown(1000);

        assertTrue(gesture.onDeadline(4000));
        assertFalse(gesture.onDeadline(4001));
    }

    @Test
    public void earlyDeadlineDoesNotTrigger() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);

        gesture.onDown(1000);

        assertFalse(gesture.onDeadline(3999));
        assertTrue(gesture.onDeadline(4000));
    }

    @Test
    public void cancelClearsPendingHold() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);
        gesture.onDown(1000);

        gesture.cancel();

        assertFalse(gesture.onDeadline(5000));
    }

    @Test
    public void invalidTimestampsDoNotTrigger() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);

        assertFalse(gesture.onDeadline(4000));
        gesture.onDown(5000);
        assertFalse(gesture.onDeadline(4999));
        assertFalse(gesture.onDeadline(9000));
    }
}
