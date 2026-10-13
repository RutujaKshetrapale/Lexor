using VehicleService.Enums;

namespace VehicleService.DTOs.Response;

public class VehicleVerificationStatusResponse
{
    public string VehicleUuid { get; set; } = string.Empty;
    public VehicleVerificationStatus Status { get; set; }
    public string? RejectionReason { get; set; }
    public bool IsEligibleForAssignment { get; set; }
    public List<VehicleDocumentType> RequiredDocuments { get; set; } = new();
    public List<VehicleDocumentType> UploadedDocuments { get; set; } = new();
    public List<VehicleDocumentType> MissingDocuments { get; set; } = new();
    public List<VehicleDocumentType> PendingReviewDocuments { get; set; } = new();
    public List<VehicleDocumentType> RejectedDocuments { get; set; } = new();
}
