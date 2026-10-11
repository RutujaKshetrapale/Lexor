namespace DriverService.Configuration;

public class OnboardingPolicySettings
{
    public const string SectionName = "Onboarding";

    public string RequiredDocuments { get; set; } = "DRIVING_LICENSE,GOVERNMENT_ID,ADDRESS_PROOF";
}
