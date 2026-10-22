package com.lexor.feedback.enums;

public enum ModerationStatus {
    VISIBLE,
    UNDER_REVIEW,
    HIDDEN,
    REMOVED;

    public boolean canTransitionTo(ModerationStatus newStatus) {
        if (this == newStatus) {
            return false;
        }
        return switch (this) {
            case VISIBLE -> newStatus == UNDER_REVIEW || newStatus == HIDDEN || newStatus == REMOVED;
            case UNDER_REVIEW -> newStatus == VISIBLE || newStatus == HIDDEN || newStatus == REMOVED;
            case HIDDEN -> newStatus == VISIBLE || newStatus == REMOVED;
            case REMOVED -> newStatus == VISIBLE; // Restoration allowed with authorized reason
        };
    }
}
