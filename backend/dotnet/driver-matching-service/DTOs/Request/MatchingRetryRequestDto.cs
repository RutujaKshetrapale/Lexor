using System.ComponentModel.DataAnnotations;

namespace DriverMatchingService.DTOs.Request;

public class MatchingRetryRequestDto
{
    [MaxLength(500, ErrorMessage = "Reason cannot exceed 500 characters")]
    public string? Reason { get; set; }

    public bool ForceCandidateReevaluation { get; set; } = false;
}
