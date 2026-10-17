package com.lexor.matching.entity;

public enum MatchingStatus {
    PENDING,
    SEARCHING,
    MATCHED,
    NO_DRIVERS_FOUND,
    CANCELLED,
    FAILED,
    EXPIRED;

    public boolean isTerminal() {
        return this == MATCHED || this == NO_DRIVERS_FOUND || this == CANCELLED || this == FAILED || this == EXPIRED;
    }

    public boolean canRetry() {
        return this == NO_DRIVERS_FOUND || this == FAILED || this == EXPIRED;
    }
}
