namespace DriverMatchingService.Configuration;

public class MatchingSettings
{
    public const string SectionName = "Matching";

    public double MaxRadiusKm { get; set; } = 10.0;
    public long MaxLocationAgeSeconds { get; set; } = 300;
    public long ReservationTtlSeconds { get; set; } = 120;
    public string InternalApiKey { get; set; } = "lexor_internal_matching_service_key_2026";
}
