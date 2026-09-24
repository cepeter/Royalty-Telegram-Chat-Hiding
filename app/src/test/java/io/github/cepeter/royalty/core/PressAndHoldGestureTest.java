package io.github.cepeter.royalty.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PressAndHoldGestureTest {
    @Test
    public void triggersAfterThreeSecondHold() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);

        gesture.onDown(1000);

        assertTrue(gesture.onUp(4000));
    }

    @Test
    public void shortPressDoesNotTrigger() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);

        gesture.onDown(1000);

        assertFalse(gesture.onUp(3999));
    }

    @Test
    public void cancelClearsPendingHold() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);
        gesture.onDown(1000);

        gesture.cancel();

        assertFalse(gesture.onUp(5000));
    }

    @Test
    public void invalidTimestampsDoNotTrigger() {
        PressAndHoldGesture gesture = new PressAndHoldGesture(3000);

        assertFalse(gesture.onUp(4000));
        gesture.onDown(5000);
        assertFalse(gesture.onUp(4999));
        assertFalse(gesture.onUp(9000));
    }
}
