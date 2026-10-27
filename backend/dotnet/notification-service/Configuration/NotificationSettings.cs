namespace NotificationService.Configuration;

public class NotificationSettings
{
    public FcmSettings Fcm { get; set; } = new();
    public SmsSettings Sms { get; set; } = new();
    public EmailSettings Email { get; set; } = new();
    public int OtpRateLimitSeconds { get; set; } = 60;
    public string DefaultLocale { get; set; } = "en";
}

public class FcmSettings
{
    public bool Enabled { get; set; } = true;
    public string ProjectId { get; set; } = "lexor-mobility-sandbox";
    public string ServerKey { get; set; } = "fcm_test_server_key_sandbox";
}

public class SmsSettings
{
    public bool Enabled { get; set; } = true;
    public string Provider { get; set; } = "MSG91_SANDBOX";
    public Msg91Settings Msg91 { get; set; } = new();
    public string AccountSid { get; set; } = "AC_test_lexor_sid";
    public string AuthToken { get; set; } = "auth_token_test_sandbox";
    public string FromNumber { get; set; } = "+15005550006";
    public int RateLimitPerHour { get; set; } = 5;
}

public class Msg91Settings
{
    public string AuthKey { get; set; } = string.Empty;
    public string TemplateId { get; set; } = string.Empty;
    public string SenderId { get; set; } = "LEXORS";
    public int OtpExpiryMinutes { get; set; } = 5;
}


public class EmailSettings
{
    public bool Enabled { get; set; } = true;
    public string Provider { get; set; } = "SMTP_SANDBOX";
    public string FromAddress { get; set; } = "no-reply@lexor.com";
    public string FromName { get; set; } = "LEXOR Mobility";
}

public class JwtSettings
{
    public string Secret { get; set; } = string.Empty;
    public string Issuer { get; set; } = "LEXOR";
    public string Audience { get; set; } = "LEXOR";
}
