using DriverService.Enums;

namespace DriverService.DTOs.Response;

public class DocumentRequirementStatus
{
    public DocumentType DocumentType { get; set; }
    public bool Required { get; set; }
    public DocumentVerificationStatus Status { get; set; }
    public long? DocumentId { get; set; }
    public string? DocumentNumber { get; set; }
    public string? RejectionReason { get; set; }
    public DateTime? UploadedAt { get; set; }
    public DateTime? VerifiedAt { get; set; }
}
