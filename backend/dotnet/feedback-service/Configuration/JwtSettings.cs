namespace FeedbackService.Configuration;

public class JwtSettings
{
    public string Secret { get; set; } = "lexor-default-jwt-secret-key-32-chars-long!";
    public string Issuer { get; set; } = "LEXOR";
    public string Audience { get; set; } = "LEXOR";
    public int ExpirationMinutes { get; set; } = 1440;
}
