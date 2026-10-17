namespace DriverMatchingService.DTOs.Response;

public class DriverCandidateResponse
{
    public string DriverUuid { get; set; } = string.Empty;
    public string FirstName { get; set; } = string.Empty;
    public string LastName { get; set; } = string.Empty;
    public decimal Rating { get; set; }
    public int TotalTrips { get; set; }
    public decimal AcceptanceRate { get; set; }
    public decimal? DistanceKm { get; set; }
    public decimal? MatchScore { get; set; }
}
