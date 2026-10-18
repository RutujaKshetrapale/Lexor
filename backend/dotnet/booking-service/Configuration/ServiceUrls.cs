namespace BookingService.Configuration;

public class ServiceUrls
{
    public const string SectionName = "ServiceUrls";

    public string RideRequestServiceUrl { get; set; } = "http://localhost:8092";
    public string DriverMatchingServiceUrl { get; set; } = "http://localhost:8094";
    public string MatchingServiceUrl
    {
        get => DriverMatchingServiceUrl;
        set => DriverMatchingServiceUrl = value;
    }
    public string DriverServiceUrl { get; set; } = "http://localhost:8086";
}
