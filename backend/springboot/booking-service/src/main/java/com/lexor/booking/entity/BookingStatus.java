package com.lexor.booking.entity;

public enum BookingStatus {
    OFFERED,
    DRIVER_ASSIGNED,
    CONFIRMED,
    ACCEPTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED_BY_RIDER,
    CANCELLED_BY_DRIVER,
    CANCELLED_BY_ADMIN,
    DECLINED_BY_DRIVER,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED ||
                this == CANCELLED_BY_RIDER ||
                this == CANCELLED_BY_DRIVER ||
                this == CANCELLED_BY_ADMIN ||
                this == DECLINED_BY_DRIVER ||
                this == EXPIRED;
    }

    public boolean canRiderCancel() {
        return this == OFFERED ||
                this == DRIVER_ASSIGNED ||
                this == CONFIRMED ||
                this == ACCEPTED ||
                this == ARRIVED;
    }

    public boolean canDriverAccept() {
        return this == OFFERED ||
                this == DRIVER_ASSIGNED ||
                this == CONFIRMED;
    }

    public boolean canDriverDecline() {
        return this == OFFERED ||
                this == DRIVER_ASSIGNED ||
                this == CONFIRMED;
    }

    public boolean canDriverArrive() {
        return this == ACCEPTED ||
                this == CONFIRMED;
    }

    public boolean canDriverStart() {
        return this == ARRIVED ||
                this == ACCEPTED;
    }

    public boolean canDriverComplete() {
        return this == IN_PROGRESS;
    }
}
