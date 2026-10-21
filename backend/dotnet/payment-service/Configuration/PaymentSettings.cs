namespace PaymentService.Configuration;

public class PaymentSettings
{
    public const string SectionName = "Payment";

    public string Provider { get; set; } = "RAZORPAY";
    public decimal CommissionPercentage { get; set; } = 20.00m;
    public string Currency { get; set; } = "INR";
    public RazorpaySettings Razorpay { get; set; } = new();
}

public class RazorpaySettings
{
    public string KeyId { get; set; } = "rzp_test_sample_key_123456";
    public string KeySecret { get; set; } = "sample_razorpay_secret_654321";
    public string WebhookSecret { get; set; } = "sample_webhook_secret_987654";
}

public class JwtSettings
{
    public const string SectionName = "Jwt";

    public string Secret { get; set; } = string.Empty;
    public string Issuer { get; set; } = "LEXOR";
    public string Audience { get; set; } = "LEXOR";
    public int ExpirationMinutes { get; set; } = 1440;
}

public class ServiceUrls
{
    public const string SectionName = "Services";

    public string BookingUrl { get; set; } = "http://localhost:8096";
    public int TimeoutSeconds { get; set; } = 5;
}
