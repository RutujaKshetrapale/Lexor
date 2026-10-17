using DriverMatchingService.Enums;

namespace DriverMatchingService.DTOs.Response;

public class MatchingAttemptResponse
{
    public string AttemptUuid { get; set; } = string.Empty;
    public string RideRequestUuid { get; set; } = string.Empty;
    public MatchingStatus Status { get; set; }
    public int AttemptNumber { get; set; }
    public int CandidatesEvaluated { get; set; }
    public DriverCandidateResponse? Driver { get; set; }
    public VehicleSummaryResponse? Vehicle { get; set; }
    public decimal? DistanceKm { get; set; }
    public decimal? MatchScore { get; set; }
    public string? FailureReason { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? CompletedAt { get; set; }
}
