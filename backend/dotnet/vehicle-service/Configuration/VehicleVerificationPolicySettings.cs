namespace VehicleService.Configuration;

public class VehicleVerificationPolicySettings
{
    public const string SectionName = "VerificationPolicy";

    public string RequiredDocuments { get; set; } = "REGISTRATION_CERTIFICATE,VEHICLE_INSURANCE,POLLUTION_UNDER_CONTROL";
}
