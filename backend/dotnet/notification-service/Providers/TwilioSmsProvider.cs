using System.Collections.Concurrent;
using Microsoft.Extensions.Options;
using NotificationService.Configuration;

namespace NotificationService.Providers;

public interface ISmsNotificationProvider
{
    string ProviderName { get; }
    ProviderDispatchResult SendSms(string destinationPhone, string messageBody);
}

public class TwilioSmsProvider : ISmsNotificationProvider
{
    private readonly NotificationSettings _settings;
    private readonly ILogger<TwilioSmsProvider> _logger;
    private readonly ConcurrentDictionary<string, long> _lastSentTimestampByPhone = new();

    public TwilioSmsProvider(IOptions<NotificationSettings> options, ILogger<TwilioSmsProvider> logger)
    {
        _settings = options.Value;
        _logger = logger;
    }

    public string ProviderName => "TWILIO_SANDBOX";

    public ProviderDispatchResult SendSms(string destinationPhone, string messageBody)
    {
        if (!_settings.Sms.Enabled)
        {
            _logger.LogInformation("SMS provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.Accepted($"SMS_DISABLED_{Guid.NewGuid()}");
        }

        if (string.IsNullOrWhiteSpace(destinationPhone))
        {
            return ProviderDispatchResult.Failure("INVALID_DESTINATION", "Destination phone cannot be empty", false);
        }

        var sid = $"SM{Guid.NewGuid():N}";
        _lastSentTimestampByPhone[destinationPhone] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        _logger.LogInformation("Submitted SMS via {Provider} [sid={Sid}] to phone={Phone}",
            ProviderName, sid, MaskPhone(destinationPhone));

        return ProviderDispatchResult.Accepted(sid);
    }

    private static string MaskPhone(string? phone) =>
        string.IsNullOrEmpty(phone) || phone.Length < 6
            ? "***"
            : $"{phone[..3]}****{phone[^2..]}";
}
