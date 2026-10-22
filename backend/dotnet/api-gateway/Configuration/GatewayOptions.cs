namespace ApiGateway.Configuration;

public class JwtOptions
{
    public const string SectionName = "Jwt";
    public string Secret { get; set; } = string.Empty;
    public string Issuer { get; set; } = "LEXOR";
    public string Audience { get; set; } = "LEXOR";
}

public class CorsOptions
{
    public const string SectionName = "Cors";
    public string AllowedOrigins { get; set; } = "http://localhost:3000,http://localhost:5173";
}

public class RateLimitingOptions
{
    public const string SectionName = "RateLimiting";
    public int PermitLimit { get; set; } = 50;
    public int QueueLimit { get; set; } = 0;
    public int WindowSeconds { get; set; } = 1;
}
