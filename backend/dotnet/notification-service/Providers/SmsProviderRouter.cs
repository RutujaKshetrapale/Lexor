using Microsoft.Extensions.Options;
using NotificationService.Configuration;

namespace NotificationService.Providers;

public class SmsProviderRouter : ISmsNotificationProvider
{
    private readonly Msg91SmsProvider _msg91Provider;
    private readonly TwilioSmsProvider _twilioProvider;
    private readonly NotificationSettings _settings;
    private readonly ILogger<SmsProviderRouter> _logger;

    public SmsProviderRouter(
        Msg91SmsProvider msg91Provider,
        TwilioSmsProvider twilioProvider,
        IOptions<NotificationSettings> options,
        ILogger<SmsProviderRouter> logger
    )
    {
        _msg91Provider = msg91Provider;
        _twilioProvider = twilioProvider;
        _settings = options.Value;
        _logger = logger;
    }

    public string ProviderName => GetActiveProvider().ProviderName;

    public bool IsLiveMode => GetActiveProvider().IsLiveMode;

    public ProviderDispatchResult SendSms(string destinationPhone, string messageBody)
    {
        var active = GetActiveProvider();
        _logger.LogInformation("Routing SMS dispatch to provider: {Provider}", active.ProviderName);
        return active.SendSms(destinationPhone, messageBody);
    }

    public ISmsNotificationProvider GetActiveProvider()
    {
        var provider = _settings.Sms.Provider;
        if (!string.IsNullOrWhiteSpace(provider) && provider.Contains("TWILIO", StringComparison.OrdinalIgnoreCase))
        {
            return _twilioProvider;
        }
        return _msg91Provider;
    }
}
