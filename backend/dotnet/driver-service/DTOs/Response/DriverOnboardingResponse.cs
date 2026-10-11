using DriverService.Enums;

namespace DriverService.DTOs.Response;

public class DriverOnboardingResponse
{
    public string? DriverUuid { get; set; }
    public OnboardingStatus OnboardingStatus { get; set; }
    public int CompletionPercentage { get; set; }
    public bool ProfileComplete { get; set; }
    public bool DocumentsComplete { get; set; }
    public bool CanSubmitForReview { get; set; }
    public bool IsEligibleForOnline { get; set; }
    public List<DocumentRequirementStatus> DocumentChecklist { get; set; } = new();
    public List<string> OutstandingTasks { get; set; } = new();
    public string? RejectionReason { get; set; }
}
