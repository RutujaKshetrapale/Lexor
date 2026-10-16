package com.lexor.riderequest.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RideRequestLifecycleTest {

    @Test
    @DisplayName("Verify valid forward state transitions in ride request lifecycle")
    void testValidStateTransitions() {
        // REQUESTED -> SEARCHING (Driver Matching picks it up)
        assertTrue(RideRequestStatus.REQUESTED.canTransitionTo(RideRequestStatus.SEARCHING));

        // SEARCHING -> MATCHED (Driver accepted)
        assertTrue(RideRequestStatus.SEARCHING.canTransitionTo(RideRequestStatus.MATCHED));

        // REQUESTED -> CANCELLED (Rider cancelled before search)
        assertTrue(RideRequestStatus.REQUESTED.canTransitionTo(RideRequestStatus.CANCELLED));

        // SEARCHING -> CANCELLED (Rider cancelled while searching)
        assertTrue(RideRequestStatus.SEARCHING.canTransitionTo(RideRequestStatus.CANCELLED));

        // REQUESTED -> EXPIRED (Timeout)
        assertTrue(RideRequestStatus.REQUESTED.canTransitionTo(RideRequestStatus.EXPIRED));

        // SEARCHING -> EXPIRED (Timeout when no driver found)
        assertTrue(RideRequestStatus.SEARCHING.canTransitionTo(RideRequestStatus.EXPIRED));
    }

    @Test
    @DisplayName("Verify invalid transitions from terminal or matched states")
    void testInvalidTransitionsFromTerminalStates() {
        // MATCHED is handed over to Booking Service; cannot transition back or cancel via request endpoint
        assertFalse(RideRequestStatus.MATCHED.canTransitionTo(RideRequestStatus.REQUESTED));
        assertFalse(RideRequestStatus.MATCHED.canTransitionTo(RideRequestStatus.SEARCHING));
        assertFalse(RideRequestStatus.MATCHED.canTransitionTo(RideRequestStatus.CANCELLED));
        assertFalse(RideRequestStatus.MATCHED.canTransitionTo(RideRequestStatus.EXPIRED));

        // CANCELLED is terminal; cannot transition anywhere
        assertFalse(RideRequestStatus.CANCELLED.canTransitionTo(RideRequestStatus.REQUESTED));
        assertFalse(RideRequestStatus.CANCELLED.canTransitionTo(RideRequestStatus.SEARCHING));
        assertFalse(RideRequestStatus.CANCELLED.canTransitionTo(RideRequestStatus.MATCHED));
        assertFalse(RideRequestStatus.CANCELLED.canTransitionTo(RideRequestStatus.EXPIRED));
        assertFalse(RideRequestStatus.CANCELLED.canTransitionTo(RideRequestStatus.CANCELLED));

        // EXPIRED is terminal; cannot transition anywhere
        assertFalse(RideRequestStatus.EXPIRED.canTransitionTo(RideRequestStatus.REQUESTED));
        assertFalse(RideRequestStatus.EXPIRED.canTransitionTo(RideRequestStatus.SEARCHING));
        assertFalse(RideRequestStatus.EXPIRED.canTransitionTo(RideRequestStatus.MATCHED));
        assertFalse(RideRequestStatus.EXPIRED.canTransitionTo(RideRequestStatus.CANCELLED));
    }

    @Test
    @DisplayName("Verify canCancel predicate accurately identifies cancellable states")
    void testCanCancelPredicate() {
        assertTrue(RideRequestStatus.REQUESTED.canCancel());
        assertTrue(RideRequestStatus.SEARCHING.canCancel());

        assertFalse(RideRequestStatus.MATCHED.canCancel());
        assertFalse(RideRequestStatus.CANCELLED.canCancel());
        assertFalse(RideRequestStatus.EXPIRED.canCancel());
    }

    @Test
    @DisplayName("Verify isTerminal predicate accurately identifies terminal states")
    void testIsTerminalPredicate() {
        assertFalse(RideRequestStatus.REQUESTED.isTerminal());
        assertFalse(RideRequestStatus.SEARCHING.isTerminal());

        assertTrue(RideRequestStatus.MATCHED.isTerminal());
        assertTrue(RideRequestStatus.CANCELLED.isTerminal());
        assertTrue(RideRequestStatus.EXPIRED.isTerminal());
    }
}
