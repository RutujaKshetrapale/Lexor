using System.Collections.Concurrent;
using System.Net.Http.Headers;
using System.Text;
using Microsoft.Extensions.Options;
using NotificationService.Configuration;

namespace NotificationService.Providers;

public class TwilioSmsProvider : ISmsNotificationProvider
{
    private static readonly HttpClient _httpClient = new();
    private readonly NotificationSettings _settings;
    private readonly ILogger<TwilioSmsProvider> _logger;
    private readonly ConcurrentDictionary<string, long> _lastSentTimestampByPhone = new();

    public TwilioSmsProvider(IOptions<NotificationSettings> options, ILogger<TwilioSmsProvider> logger)
    {
        _settings = options.Value;
        _logger = logger;
    }

    public string ProviderName => IsLiveMode ? "TWILIO" : "TWILIO_SANDBOX";

    public bool IsLiveMode
    {
        get
        {
            var sms = _settings.Sms;
            return sms.Enabled &&
                   !string.IsNullOrWhiteSpace(sms.Provider) &&
                   !sms.Provider.Contains("SANDBOX", StringComparison.OrdinalIgnoreCase) &&
                   !string.IsNullOrWhiteSpace(sms.AccountSid) &&
                   sms.AccountSid.StartsWith("AC") &&
                   !sms.AccountSid.Contains("test") &&
                   !string.IsNullOrWhiteSpace(sms.AuthToken) &&
                   !sms.AuthToken.Contains("placeholder");
        }
    }

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

        if (IsLiveMode)
        {
            return ExecuteLiveDispatch(destinationPhone, messageBody);
        }

        var sid = $"SM{Guid.NewGuid():N}";
        _lastSentTimestampByPhone[destinationPhone] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        _logger.LogInformation("Submitted SMS via {Provider} [sid={Sid}] to phone={Phone}",
            ProviderName, sid, MaskPhone(destinationPhone));

        return ProviderDispatchResult.Accepted(sid);
    }

    private ProviderDispatchResult ExecuteLiveDispatch(string destinationPhone, string messageBody)
    {
        var sid = _settings.Sms.AccountSid;
        var token = _settings.Sms.AuthToken;
        var fromNumber = _settings.Sms.FromNumber;
        var url = $"https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json";

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++)
        {
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Post, url);
                var authBytes = Encoding.ASCII.GetBytes($"{sid}:{token}");
                request.Headers.Authorization = new AuthenticationHeaderValue("Basic", Convert.ToBase64String(authBytes));

                var formData = new Dictionary<string, string>
                {
                    { "To", destinationPhone },
                    { "From", fromNumber },
                    { "Body", messageBody }
                };
                request.Content = new FormUrlEncodedContent(formData);

                var response = _httpClient.Send(request);
                if (response.IsSuccessStatusCode)
                {
                    var msgSid = $"SM{Guid.NewGuid():N}";
                    _lastSentTimestampByPhone[destinationPhone] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                    _logger.LogInformation("Dispatched live Twilio SMS [sid={Sid}] to phone={Phone}",
                        msgSid, MaskPhone(destinationPhone));
                    return ProviderDispatchResult.Accepted(msgSid);
                }

                if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
                {
                    return ProviderDispatchResult.Failure(
                        $"TWILIO_REJECTED_{(int)response.StatusCode}",
                        $"Provider rejected request: {response.ReasonPhrase}",
                        false
                    );
                }
            }
            catch (Exception ex) when (attempt < maxAttempts)
            {
                _logger.LogWarning(ex, "Transient error dispatching Twilio SMS on attempt {Attempt}", attempt);
                Thread.Sleep(500 * attempt);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error dispatching Twilio SMS");
            }
        }

        return ProviderDispatchResult.Failure("TWILIO_OUTAGE", "Twilio SMS provider dispatch failed after retries", true);
    }

    public static string MaskPhone(string? phone) =>
        string.IsNullOrEmpty(phone) || phone.Length < 6
            ? "***"
            : $"{phone[..3]}****{phone[^2..]}";
}
