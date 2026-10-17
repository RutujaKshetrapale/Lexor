namespace DriverMatchingService.Configuration;

public class ServiceUrls
{
    public const string SectionName = "Services";

    public string RideRequestServiceUrl { get; set; } = "http://localhost:8092";
    public string DriverServiceUrl { get; set; } = "http://localhost:8086";
    public int TimeoutSeconds { get; set; } = 3;
}
