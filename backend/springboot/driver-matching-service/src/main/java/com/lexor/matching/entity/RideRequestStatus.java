package com.lexor.matching.entity;

public enum RideRequestStatus {
    REQUESTED,
    SEARCHING,
    MATCHED,
    CANCELLED,
    EXPIRED;

    public boolean isEligibleForMatching() {
        return this == REQUESTED || this == SEARCHING;
    }

    public boolean isTerminal() {
        return this == MATCHED || this == CANCELLED || this == EXPIRED;
    }
}
