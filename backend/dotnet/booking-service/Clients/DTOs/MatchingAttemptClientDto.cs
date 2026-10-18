namespace BookingService.Clients.DTOs;

public class MatchingAttemptClientDto
{
    public string AttemptUuid { get; set; } = string.Empty;
    public string RideRequestUuid { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public int? AttemptNumber { get; set; }
    public int? CandidatesEvaluated { get; set; }
    public DriverCandidateClientDto? Driver { get; set; }
    public VehicleClientDto? Vehicle { get; set; }
    public decimal? DistanceKm { get; set; }
    public decimal? MatchScore { get; set; }
    public string? FailureReason { get; set; }
    public DateTime? CreatedAt { get; set; }
    public DateTime? CompletedAt { get; set; }
}
