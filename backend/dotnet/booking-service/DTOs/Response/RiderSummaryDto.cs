namespace BookingService.DTOs.Response;

public class RiderSummaryDto
{
    public string RiderUuid { get; set; } = string.Empty;
    public string? FirstName { get; set; }
    public string? LastName { get; set; }
    public string? Email { get; set; }
    public string? PhoneNumber { get; set; }
    public decimal? Rating { get; set; }
}
