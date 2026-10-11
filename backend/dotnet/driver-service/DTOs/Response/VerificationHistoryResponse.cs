namespace DriverService.DTOs.Response;

public class VerificationHistoryResponse
{
    public long Id { get; set; }
    public long DriverId { get; set; }
    public long? DocumentId { get; set; }
    public string ActionType { get; set; } = string.Empty;
    public string PreviousStatus { get; set; } = string.Empty;
    public string NewStatus { get; set; } = string.Empty;
    public long ReviewedByAdminId { get; set; }
    public string? ReviewedByAdminName { get; set; }
    public string? Reason { get; set; }
    public DateTime CreatedAt { get; set; }
}
