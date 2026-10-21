using Microsoft.Extensions.Options;
using NotificationService.Configuration;

namespace NotificationService.Providers;

public interface IEmailNotificationProvider
{
    string ProviderName { get; }
    ProviderDispatchResult SendEmail(string toAddress, string subject, string bodyHtml, string bodyText);
}

public class SmtpEmailProvider : IEmailNotificationProvider
{
    private readonly NotificationSettings _settings;
    private readonly ILogger<SmtpEmailProvider> _logger;

    public SmtpEmailProvider(IOptions<NotificationSettings> options, ILogger<SmtpEmailProvider> logger)
    {
        _settings = options.Value;
        _logger = logger;
    }

    public string ProviderName => "SMTP_SANDBOX";

    public ProviderDispatchResult SendEmail(string toAddress, string subject, string bodyHtml, string bodyText)
    {
        if (!_settings.Email.Enabled)
        {
            _logger.LogInformation("Email provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.Accepted($"EMAIL_DISABLED_{Guid.NewGuid()}");
        }

        if (string.IsNullOrWhiteSpace(toAddress) || !toAddress.Contains('@'))
        {
            return ProviderDispatchResult.Failure("INVALID_EMAIL", "Invalid destination email address", false);
        }

        var messageId = $"<{Guid.NewGuid()}@lexor.com>";
        _logger.LogInformation("Dispatched Email via {Provider} [messageId={MessageId}] to email={Email}, subject='{Subject}'",
            ProviderName, messageId, MaskEmail(toAddress), subject);

        return ProviderDispatchResult.Accepted(messageId);
    }

    private static string MaskEmail(string? email)
    {
        if (string.IsNullOrEmpty(email) || !email.Contains('@')) return "***";
        var parts = email.Split('@');
        return $"{parts[0][0]}***@{parts[1]}";
    }
}
