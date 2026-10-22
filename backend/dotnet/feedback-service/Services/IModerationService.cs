using System.Security.Claims;
using FeedbackService.Domain.Enums;
using FeedbackService.DTOs;

namespace FeedbackService.Services;

public interface IModerationService
{
    Task<FeedbackReportResponse> ReportReviewAsync(ClaimsPrincipal reporter, string reviewUuid, ReportReviewRequest request);
    Task<PageResponse<FeedbackReportResponse>> GetReportsAsync(ReportStatus? status, int page, int size);
    Task<FeedbackResponse> ApplyModerationActionAsync(ClaimsPrincipal moderator, string reviewUuid, ModerationActionRequest request);
    Task<List<ModerationHistoryResponse>> GetModerationHistoryAsync(string reviewUuid);
    Task<FeedbackAnalyticsResponse> GetAnalyticsAsync();
}
