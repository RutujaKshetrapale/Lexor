using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;

namespace FeedbackService.Services;

public interface IRatingCalculationService
{
    Task UpdateRatingSummaryOnNewFeedbackAsync(RideFeedback feedback);
    Task UpdateRatingSummaryOnRatingEditAsync(RideFeedback feedback, int oldRating);
    Task RecalculateSummaryForTargetAsync(string targetUuid, FeedbackRole targetRole);
    Task<DriverRatingSummaryResponse> GetDriverSummaryAsync(string driverUuid);
    Task<RiderRatingSummaryResponse> GetRiderSummaryAsync(string riderUuid);
}
