namespace BookingService.Configuration;

public class BookingSettings
{
    public const string SectionName = "BookingSettings";

    public string InternalApiKey { get; set; } = string.Empty;
    public int OtpExpirationMinutes { get; set; } = 120;
    public int ConnectTimeoutSeconds { get; set; } = 3;
    public int ReadTimeoutSeconds { get; set; } = 5;
}
