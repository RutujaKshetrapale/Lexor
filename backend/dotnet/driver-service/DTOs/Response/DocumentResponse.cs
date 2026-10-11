using DriverService.Enums;

namespace DriverService.DTOs.Response;

public class DocumentResponse
{
    public long Id { get; set; }
    public long DriverId { get; set; }
    public DocumentType DocumentType { get; set; }
    public string? DocumentNumber { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string ContentType { get; set; } = string.Empty;
    public long FileSize { get; set; }
    public DateOnly? IssueDate { get; set; }
    public DateOnly? ExpiryDate { get; set; }
    public string? IssuingAuthority { get; set; }
    public DocumentVerificationStatus VerificationStatus { get; set; }
    public string? RejectionReason { get; set; }
    public string? ReviewNotes { get; set; }
    public long? ReviewedByAdminId { get; set; }
    public DateTime? ReviewedAt { get; set; }
    public int Version { get; set; }
    public bool? IsCurrent { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}
