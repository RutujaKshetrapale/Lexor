package com.lexor.booking.entity;

public enum RideRequestStatus {
    REQUESTED,
    SEARCHING,
    MATCHED,
    CANCELLED,
    EXPIRED;

    public boolean isEligibleForBooking() {
        return this == MATCHED || this == REQUESTED || this == SEARCHING;
    }

    public boolean isTerminal() {
        return this == CANCELLED || this == EXPIRED;
    }
}
