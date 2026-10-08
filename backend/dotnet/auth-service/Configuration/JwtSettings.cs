namespace AuthService.Configuration;

public class JwtSettings
{
    public const string SectionName = "Jwt";

    public string Secret { get; set; } = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    public string Issuer { get; set; } = "LEXOR";
    public string Audience { get; set; } = "LEXOR";
    public int ExpirationMinutes { get; set; } = 1440; // 24 Hours (1440 Minutes)
}
