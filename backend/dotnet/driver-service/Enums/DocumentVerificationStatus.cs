namespace DriverService.Enums;

public enum DocumentVerificationStatus
{
    NOT_SUBMITTED,
    PENDING_REVIEW,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED,
    EXPIRED,
    RESUBMISSION_REQUIRED
}
