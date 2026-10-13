using VehicleService.Enums;

namespace VehicleService.DTOs.Response;

public class VehicleVerificationHistoryResponse
{
    public long Id { get; set; }
    public string VehicleUuid { get; set; } = string.Empty;
    public long? DocumentId { get; set; }
    public VehicleDocumentType? DocumentType { get; set; }
    public string ActionType { get; set; } = string.Empty;
    public string PreviousStatus { get; set; } = string.Empty;
    public string NewStatus { get; set; } = string.Empty;
    public string? ReviewedByAdminName { get; set; }
    public string? Reason { get; set; }
    public DateTime CreatedAt { get; set; }
}
