namespace DriverMatchingService.DTOs.Response;

public class LocationUpdateResponse
{
    public string DriverUuid { get; set; } = string.Empty;
    public decimal Latitude { get; set; }
    public decimal Longitude { get; set; }
    public decimal? Heading { get; set; }
    public decimal? SpeedKmh { get; set; }
    public DateTime RecordedAt { get; set; }
    public string Status { get; set; } = "SUCCESS";
}
