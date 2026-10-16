package com.lexor.riderequest.entity;

public enum RideRequestStatus {
    REQUESTED,
    SEARCHING,
    MATCHED,
    CANCELLED,
    EXPIRED;

    public boolean canCancel() {
        return this == REQUESTED || this == SEARCHING;
    }

    public boolean isTerminal() {
        return this == MATCHED || this == CANCELLED || this == EXPIRED;
    }

    public boolean canTransitionTo(RideRequestStatus newStatus) {
        if (this == newStatus) {
            return false;
        }
        return switch (this) {
            case REQUESTED -> newStatus == SEARCHING || newStatus == CANCELLED || newStatus == EXPIRED;
            case SEARCHING -> newStatus == MATCHED || newStatus == CANCELLED || newStatus == EXPIRED;
            case MATCHED, CANCELLED, EXPIRED -> false;
        };
    }
}
