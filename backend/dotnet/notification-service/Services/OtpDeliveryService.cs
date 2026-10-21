using System.Collections.Concurrent;
using Microsoft.Extensions.Options;
using NotificationService.Configuration;
using NotificationService.DTOs;
using NotificationService.Exceptions;
using NotificationService.Providers;

namespace NotificationService.Services;

public interface IOtpDeliveryService
{
    ApiResponse<string> DeliverOtp(OtpDeliveryRequest request);
}

public class OtpDeliveryService : IOtpDeliveryService
{
    private readonly ISmsNotificationProvider _smsProvider;
    private readonly IEmailNotificationProvider _emailProvider;
    private readonly NotificationSettings _settings;
    private readonly ILogger<OtpDeliveryService> _logger;
    private readonly ConcurrentDictionary<string, long> _rateLimitTracker = new();

    public OtpDeliveryService(
        ISmsNotificationProvider smsProvider,
        IEmailNotificationProvider emailProvider,
        IOptions<NotificationSettings> options,
        ILogger<OtpDeliveryService> logger
    )
    {
        _smsProvider = smsProvider;
        _emailProvider = emailProvider;
        _settings = options.Value;
        _logger = logger;
    }

    public ApiResponse<string> DeliverOtp(OtpDeliveryRequest request)
    {
        var phone = request.RecipientPhone;
        var email = request.RecipientEmail;

        if (string.IsNullOrWhiteSpace(phone) && string.IsNullOrWhiteSpace(email))
        {
            throw new BadRequestException("Either recipientPhone or recipientEmail must be provided");
        }

        var destination = !string.IsNullOrWhiteSpace(phone) ? phone.Trim() : email!.Trim();

        long minIntervalMs = _settings.OtpRateLimitSeconds * 1000L;
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

        if (_rateLimitTracker.TryGetValue(destination, out var lastSent) && (now - lastSent) < minIntervalMs)
        {
            long waitSeconds = ((lastSent + minIntervalMs) - now) / 1000L;
            _logger.LogWarning("Rate limit triggered for OTP delivery to destination: {Destination}", MaskDestination(destination));
            throw new RateLimitExceededException($"Please wait {waitSeconds} seconds before requesting another OTP");
        }

        if (!string.IsNullOrWhiteSpace(phone))
        {
            var smsBody = $"LEXOR: Your verification OTP is {request.OtpCode}. Valid for {request.ExpirySeconds / 60} minutes. Do NOT share with anyone.";
            var result = _smsProvider.SendSms(phone, smsBody);
            _rateLimitTracker[phone] = now;
            _logger.LogInformation("Dispatched OTP via SMS to {Destination} with status {Status}",
                MaskDestination(phone), result.Status);
        }

        if (!string.IsNullOrWhiteSpace(email))
        {
            var subject = "Your LEXOR Verification Code";
            var emailText = $"Hello,\n\nYour LEXOR verification OTP is: {request.OtpCode}\n\nValid for {request.ExpirySeconds / 60} minutes.";
            var emailHtml = $"<p>Hello,</p><p>Your LEXOR verification OTP is: <strong>{request.OtpCode}</strong></p>";
            var result = _emailProvider.SendEmail(email, subject, emailHtml, emailText);
            _rateLimitTracker[email] = now;
            _logger.LogInformation("Dispatched OTP via Email to {Destination} with status {Status}",
                MaskDestination(email), result.Status);
        }

        return ApiResponse<string>.Ok("OTP dispatched successfully to destination");
    }

    private static string MaskDestination(string? dest)
    {
        if (string.IsNullOrEmpty(dest) || dest.Length < 5) return "***";
        if (dest.Contains('@'))
        {
            var parts = dest.Split('@');
            return $"{parts[0][0]}***@{parts[1]}";
        }
        return $"{dest[..3]}****{dest[^2..]}";
    }
}
