using System.ComponentModel.DataAnnotations;

namespace BookingService.DTOs.Request;

public class CompleteTripDto
{
    [Range(0.0, 10000.0, ErrorMessage = "Actual distance must be non-negative")]
    public decimal? ActualDistanceKm { get; set; }

    [Range(0, 100000, ErrorMessage = "Actual duration minutes must be non-negative")]
    public int? ActualDurationMinutes { get; set; }
}
