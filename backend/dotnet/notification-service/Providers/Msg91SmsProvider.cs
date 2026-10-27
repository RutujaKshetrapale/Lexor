using System.Collections.Concurrent;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.Extensions.Options;
using NotificationService.Configuration;

namespace NotificationService.Providers;

public class Msg91SmsProvider : ISmsNotificationProvider
{
    private static readonly HttpClient _httpClient = new();
    private readonly NotificationSettings _settings;
    private readonly ILogger<Msg91SmsProvider> _logger;
    private readonly ConcurrentDictionary<string, long> _lastSentTimestampByPhone = new();

    public Msg91SmsProvider(IOptions<NotificationSettings> options, ILogger<Msg91SmsProvider> logger)
    {
        _settings = options.Value;
        _logger = logger;
    }

    public string ProviderName => IsLiveMode ? "MSG91" : "MSG91_SANDBOX";

    public bool IsLiveMode
    {
        get
        {
            var sms = _settings.Sms;
            return sms.Enabled &&
                   !string.IsNullOrWhiteSpace(sms.Provider) &&
                   !sms.Provider.Contains("SANDBOX", StringComparison.OrdinalIgnoreCase) &&
                   !string.IsNullOrWhiteSpace(sms.Msg91?.AuthKey) &&
                   !sms.Msg91.AuthKey.StartsWith("your_");
        }
    }

    public ProviderDispatchResult SendSms(string destinationPhone, string messageBody)
    {
        if (!_settings.Sms.Enabled)
        {
            _logger.LogInformation("MSG91 SMS provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.Accepted($"MSG91_DISABLED_{Guid.NewGuid()}");
        }

        if (string.IsNullOrWhiteSpace(destinationPhone))
        {
            return ProviderDispatchResult.Failure("INVALID_DESTINATION", "Destination phone cannot be empty", false);
        }

        string normalized = NormalizePhoneNumber(destinationPhone);
        if (normalized.Length < 10)
        {
            return ProviderDispatchResult.Failure("MALFORMED_PHONE", "Destination phone must contain at least 10 digits", false);
        }

        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        long cooldownMs = _settings.OtpRateLimitSeconds * 1000L;
        if (_lastSentTimestampByPhone.TryGetValue(normalized, out long lastSent) && (now - lastSent) < cooldownMs)
        {
            long remaining = (lastSent + cooldownMs - now) / 1000L;
            return ProviderDispatchResult.Failure(
                "RATE_LIMIT_COOLDOWN",
                $"SMS resend cooldown active for {remaining} seconds",
                false
            );
        }

        if (IsLiveMode)
        {
            return ExecuteLiveDispatch(normalized, messageBody);
        }

        // Sandbox Mode
        var reqId = $"msg91_req_{Guid.NewGuid():N}";
        _lastSentTimestampByPhone[normalized] = now;

        _logger.LogInformation("Submitted SMS via {Provider} [reqId={ReqId}] to phone={Phone}",
            ProviderName, reqId, MaskPhone(destinationPhone));

        return ProviderDispatchResult.Accepted(reqId);
    }

    private ProviderDispatchResult ExecuteLiveDispatch(string normalizedPhone, string messageBody)
    {
        var msg91 = _settings.Sms.Msg91;
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++)
        {
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Post, "https://control.msg91.com/api/v5/flow/");
                request.Headers.Add("authkey", msg91.AuthKey);

                var payload = new
                {
                    flow_id = msg91.TemplateId,
                    sender = string.IsNullOrWhiteSpace(msg91.SenderId) ? "LEXORS" : msg91.SenderId,
                    mobiles = normalizedPhone,
                    var1 = messageBody
                };

                request.Content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = _httpClient.Send(request);

                if (response.IsSuccessStatusCode)
                {
                    var reqId = $"msg91_live_{Guid.NewGuid():N}";
                    _lastSentTimestampByPhone[normalizedPhone] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                    _logger.LogInformation("Successfully dispatched live SMS via MSG91 [reqId={ReqId}] to phone={Phone}",
                        reqId, MaskPhone(normalizedPhone));
                    return ProviderDispatchResult.Accepted(reqId);
                }

                if ((int)response.StatusCode >= 400 && (int)response.StatusCode < 500)
                {
                    return ProviderDispatchResult.Failure(
                        $"MSG91_REJECTED_{(int)response.StatusCode}",
                        $"Provider rejected request: {response.ReasonPhrase}",
                        false
                    );
                }
            }
            catch (Exception ex) when (attempt < maxAttempts)
            {
                _logger.LogWarning(ex, "Transient error dispatching MSG91 SMS on attempt {Attempt}/{MaxAttempts}", attempt, maxAttempts);
                Thread.Sleep(500 * attempt);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error dispatching MSG91 SMS on final attempt");
            }
        }

        return ProviderDispatchResult.Failure("MSG91_OUTAGE", "MSG91 SMS provider dispatch failed after retries", true);
    }

    public static string NormalizePhoneNumber(string phone)
    {
        if (string.IsNullOrWhiteSpace(phone)) return string.Empty;
        var digits = Regex.Replace(phone, @"[^\d]", "");
        return digits.Length == 10 ? "91" + digits : digits;
    }

    private static string MaskPhone(string? phone) =>
        string.IsNullOrEmpty(phone) || phone.Length < 6
            ? "***"
            : $"{phone[..3]}****{phone[^2..]}";
}
