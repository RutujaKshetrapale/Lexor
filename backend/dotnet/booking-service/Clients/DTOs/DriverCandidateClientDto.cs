namespace BookingService.Clients.DTOs;

public class DriverCandidateClientDto
{
    public string DriverUuid { get; set; } = string.Empty;
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
    public string? PhoneNumber { get; set; }
    public decimal? Rating { get; set; }
    public int? TotalTrips { get; set; }
}
