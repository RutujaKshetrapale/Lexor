namespace FeedbackService.Configuration;

public class JwtSettings
{
    public string Secret { get; set; } = string.Empty;
    public string Issuer { get; set; } = "LEXOR";
    public string Audience { get; set; } = "LEXOR";
    public int ExpirationMinutes { get; set; } = 1440;
}
