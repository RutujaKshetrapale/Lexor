namespace RideRequestService.Configuration;

public class ServiceUrls
{
    public const string SectionName = "Services";

    public string RideTypeServiceUrl { get; set; } = "http://localhost:8089";
    public int TimeoutSeconds { get; set; } = 3;
}
