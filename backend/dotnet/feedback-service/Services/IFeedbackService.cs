using System.Security.Claims;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;

namespace FeedbackService.Services;

public interface IFeedbackService
{
    Task<FeedbackResponse> SubmitRiderFeedbackAsync(ClaimsPrincipal rider, string rideUuid, SubmitRatingRequest request, string? authHeader = null);
    Task<FeedbackResponse> SubmitDriverFeedbackAsync(ClaimsPrincipal driver, string rideUuid, SubmitRatingRequest request, string? authHeader = null);
    Task<PageResponse<FeedbackResponse>> GetMySubmittedFeedbackAsync(ClaimsPrincipal user, int page, int size);
    Task<List<PendingFeedbackItemResponse>> GetMyPendingFeedbackAsync(ClaimsPrincipal user, string? authHeader = null);
    Task<FeedbackResponse> GetMyReviewByUuidAsync(ClaimsPrincipal user, string reviewUuid);
    Task<FeedbackResponse> UpdateMyReviewAsync(ClaimsPrincipal user, string reviewUuid, UpdateRatingRequest request);
    Task<PageResponse<FeedbackResponse>> GetDriverReceivedFeedbackAsync(ClaimsPrincipal driver, int page, int size);
    Task<PageResponse<FeedbackResponse>> GetAdminFeedbackAsync(string? reviewerUuid, string? targetUuid, ModerationStatus? status, int page, int size);
    Task<FeedbackResponse> GetAdminFeedbackByUuidAsync(string reviewUuid);
    Task<List<FeedbackTagDto>> GetFeedbackTagsAsync(string? targetRole = null);
}
