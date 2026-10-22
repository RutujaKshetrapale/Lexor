using FeedbackService.Domain.Entities;
using FeedbackService.Domain.Enums;

namespace FeedbackService.DTOs;

public class FeedbackReportResponse
{
    public string ReportUuid { get; set; } = string.Empty;
    public string FeedbackUuid { get; set; } = string.Empty;
    public string ReporterUuid { get; set; } = string.Empty;
    public ReportReason Reason { get; set; }
    public string? Comments { get; set; }
    public ReportStatus Status { get; set; }
    public string? ResolutionNotes { get; set; }
    public string? ResolvedBy { get; set; }
    public DateTime? ResolvedAt { get; set; }
    public DateTime CreatedAt { get; set; }

    public static FeedbackReportResponse FromEntity(FeedbackReport r)
    {
        return new FeedbackReportResponse
        {
            ReportUuid = r.ReportUuid,
            FeedbackUuid = r.Feedback?.FeedbackUuid ?? string.Empty,
            ReporterUuid = r.ReporterUuid,
            Reason = r.Reason,
            Comments = r.Comments,
            Status = r.Status,
            ResolutionNotes = r.ResolutionNotes,
            ResolvedBy = r.ResolvedBy,
            ResolvedAt = r.ResolvedAt,
            CreatedAt = r.CreatedAt
        };
    }
}
