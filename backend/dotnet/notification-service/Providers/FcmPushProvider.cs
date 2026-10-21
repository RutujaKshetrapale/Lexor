using Microsoft.Extensions.Options;
using NotificationService.Configuration;
using NotificationService.Domain.Enums;

namespace NotificationService.Providers;

public interface IPushNotificationProvider
{
    string ProviderName { get; }
    ProviderDispatchResult SendPushNotification(
        string deviceToken,
        DevicePlatform platform,
        string title,
        string body,
        string? deepLink,
        Dictionary<string, string>? data
    );
    bool IsTokenInvalidError(string? errorCode);
}

public class FcmPushProvider : IPushNotificationProvider
{
    private readonly NotificationSettings _settings;
    private readonly ILogger<FcmPushProvider> _logger;

    public FcmPushProvider(IOptions<NotificationSettings> options, ILogger<FcmPushProvider> logger)
    {
        _settings = options.Value;
        _logger = logger;
    }

    public string ProviderName => "FCM_SANDBOX";

    public ProviderDispatchResult SendPushNotification(
        string deviceToken,
        DevicePlatform platform,
        string title,
        string body,
        string? deepLink,
        Dictionary<string, string>? data
    )
    {
        if (!_settings.Fcm.Enabled)
        {
            _logger.LogInformation("FCM Push provider disabled in configuration, skipping dispatch");
            return ProviderDispatchResult.Accepted($"FCM_DISABLED_{Guid.NewGuid()}");
        }

        if (string.IsNullOrWhiteSpace(deviceToken))
        {
            return ProviderDispatchResult.Failure("INVALID_ARGUMENT", "Device token cannot be empty", false);
        }

        if (deviceToken.StartsWith("invalid_") || deviceToken == "expired_token")
        {
            _logger.LogWarning("FCM rejected invalid/expired device token: {Token}", MaskToken(deviceToken));
            return ProviderDispatchResult.Failure("UNREGISTERED", "Device token is unregistered or expired", false);
        }

        var messageId = $"projects/{_settings.Fcm.ProjectId}/messages/fcm_{Guid.NewGuid():N}";
        _logger.LogInformation("Dispatched FCM Push notification [messageId={MessageId}] to platform={Platform}, token={Token}",
            messageId, platform, MaskToken(deviceToken));

        return ProviderDispatchResult.Accepted(messageId);
    }

    public bool IsTokenInvalidError(string? errorCode) =>
        string.Equals("UNREGISTERED", errorCode, StringComparison.OrdinalIgnoreCase) ||
        string.Equals("INVALID_ARGUMENT", errorCode, StringComparison.OrdinalIgnoreCase) ||
        string.Equals("SENDER_ID_MISMATCH", errorCode, StringComparison.OrdinalIgnoreCase);

    private static string MaskToken(string? token) =>
        string.IsNullOrEmpty(token) || token.Length < 10
            ? "***"
            : $"{token[..4]}...{token[^4..]}";
}
