package io.github.cepeter.royalty.core;
import static org.junit.Assert.*;
import org.junit.Test;
public final class ActivityVisibilityTest {
    @Test public void simpleRotationInvalidatesDeferredConcealment() {
        ActivityVisibility v = new ActivityVisibility(); Object old = new Object(), replacement = new Object();
        v.started(old); assertFalse(v.stopped(old, true)); long token = v.pendingGeneration();
        v.started(replacement); assertFalse(v.reconcile(token)); assertTrue(v.stopped(replacement, false));
    }
    @Test public void interruptedRecreationEventuallyBackgroundsOnlyOnce() {
        ActivityVisibility v = new ActivityVisibility(); Object a = new Object();
        v.started(a); assertFalse(v.stopped(a, true)); long token = v.pendingGeneration();
        assertTrue(v.reconcile(token)); assertFalse(v.reconcile(token));
    }
    @Test public void duplicateStopsAndOldReconciliationCannotConcealNewGeneration() {
        ActivityVisibility v = new ActivityVisibility(); Object a = new Object(), b = new Object();
        v.started(a); v.started(a); assertFalse(v.stopped(a, true)); long old = v.pendingGeneration();
        assertFalse(v.stopped(a, false)); v.started(b); assertFalse(v.stopped(b, true));
        assertFalse(v.reconcile(old)); assertTrue(v.reconcile(v.pendingGeneration()));
    }
    @Test public void overlappingActivitiesLastOrdinaryStopBackgroundsImmediately() {
        ActivityVisibility v = new ActivityVisibility(); Object a = new Object(), b = new Object();
        v.started(a); v.started(b); assertFalse(v.stopped(a, true)); assertTrue(v.stopped(b, false));
        assertEquals(-1, v.pendingGeneration());
    }
}
