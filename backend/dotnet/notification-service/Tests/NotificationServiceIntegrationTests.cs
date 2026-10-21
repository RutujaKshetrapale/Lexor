using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using NotificationService.Configuration;
using NotificationService.Domain.Entities;
using NotificationService.Domain.Enums;
using NotificationService.DTOs;
using NotificationService.Exceptions;
using NotificationService.Providers;
using NotificationService.Services;
using Xunit;

namespace NotificationService.Tests;

public class NotificationServiceIntegrationTests
{
    private readonly NotificationSettings _settings;
    private readonly string _riderUuid = "11111111-1111-4111-8111-111111111111";
    private readonly string _otherRiderUuid = "22222222-2222-4222-8222-222222222222";

    public NotificationServiceIntegrationTests()
    {
        _settings = new NotificationSettings
        {
            Fcm = new FcmSettings { Enabled = true, ProjectId = "lexor-sandbox", ServerKey = "fcm_key" },
            Sms = new SmsSettings { Enabled = true, Provider = "TWILIO_SANDBOX", FromNumber = "+15005550006" },
            Email = new EmailSettings { Enabled = true, Provider = "SMTP_SANDBOX", FromAddress = "no-reply@lexor.com" },
            OtpRateLimitSeconds = 60,
            DefaultLocale = "en"
        };
    }

    private (
        NotificationService.Services.NotificationService notifService,
        DeviceService deviceService,
        PreferenceService prefService,
        OtpDeliveryService otpService,
        EventIngestionService eventService,
        TemplateService tplService
    ) CreateServices(string dbName)
    {
        var db = TestDbContextFactory.Create(dbName);
        var options = Options.Create(_settings);

        var pushProvider = new FcmPushProvider(options, NullLogger<FcmPushProvider>.Instance);
        var smsProvider = new TwilioSmsProvider(options, NullLogger<TwilioSmsProvider>.Instance);
        var emailProvider = new SmtpEmailProvider(options, NullLogger<SmtpEmailProvider>.Instance);
        var realtime = new RealtimeNotifier(NullLogger<RealtimeNotifier>.Instance);

        var prefService = new PreferenceService(db, NullLogger<PreferenceService>.Instance);
        var deviceService = new DeviceService(db, NullLogger<DeviceService>.Instance);
        var tplService = new TemplateService(db);
        var notifService = new NotificationService.Services.NotificationService(
            db, prefService, deviceService, pushProvider, realtime, NullLogger<NotificationService.Services.NotificationService>.Instance);
        var otpService = new OtpDeliveryService(smsProvider, emailProvider, options, NullLogger<OtpDeliveryService>.Instance);
        var eventService = new EventIngestionService(
            db, notifService, prefService, tplService, NullLogger<EventIngestionService>.Instance);

        // Seed templates if needed
        if (!db.NotificationTemplates.Any())
        {
            db.NotificationTemplates.AddRange(
                new NotificationTemplate { TemplateCode = "DRIVER_ASSIGNED", Channel = NotificationChannel.PUSH, Locale = "en", TitleTemplate = "Driver Assigned!", BodyTemplate = "Your driver {driverName} is on the way." },
                new NotificationTemplate { TemplateCode = "DRIVER_ASSIGNED", Channel = NotificationChannel.IN_APP, Locale = "en", TitleTemplate = "Driver Assigned", BodyTemplate = "Your driver {driverName} has accepted your ride." },
                new NotificationTemplate { TemplateCode = "DRIVER_ASSIGNED", Channel = NotificationChannel.PUSH, Locale = "hi", TitleTemplate = "चालक नियुक्त!", BodyTemplate = "आपके चालक {driverName} रास्ते में हैं।" },
                new NotificationTemplate { TemplateCode = "RIDE_COMPLETED", Channel = NotificationChannel.IN_APP, Locale = "en", TitleTemplate = "Trip Completed", BodyTemplate = "Your ride has concluded. Fare: INR {fare}." }
            );
            db.SaveChanges();
        }

        return (notifService, deviceService, prefService, otpService, eventService, tplService);
    }

    [Fact]
    public async Task DirectDispatch_CreatesNotificationAndInbox()
    {
        var (notifService, _, _, _, _, _) = CreateServices(nameof(DirectDispatch_CreatesNotificationAndInbox));

        var req = new DirectNotificationRequest(
            _riderUuid,
            RecipientRole.RIDER,
            "RIDE_COMPLETED",
            NotificationCategory.RIDE,
            "Trip Completed",
            "Your trip to City Center has finished. Fare: INR 250.",
            null, null, null, null, null
        );

        var res = await notifService.DispatchNotificationAsync(req);

        Assert.NotNull(res);
        Assert.Equal("Trip Completed", res.Title);
        Assert.Equal(NotificationStatus.COMPLETED, res.Status);

        var inbox = await notifService.GetUserInboxAsync(_riderUuid, null, null, 0, 10);
        Assert.Single(inbox.Content);
        Assert.Equal("Trip Completed", inbox.Content[0].Title);
        Assert.False(inbox.Content[0].IsRead);
    }

    [Fact]
    public async Task GetUnreadCount_ReturnsAccurateCount()
    {
        var (notifService, _, _, _, _, _) = CreateServices(nameof(GetUnreadCount_ReturnsAccurateCount));

        for (int i = 1; i <= 2; i++)
        {
            await notifService.DispatchNotificationAsync(new DirectNotificationRequest(
                _riderUuid, RecipientRole.RIDER, "ALERT", NotificationCategory.SYSTEM,
                $"Alert #{i}", $"Message {i}", null, null, null, null, null
            ));
        }

        var unread = await notifService.GetUnreadCountAsync(_riderUuid);
        Assert.Equal(2, unread.UnreadCount);
    }

    [Fact]
    public async Task MarkAsRead_UpdatesNotification()
    {
        var (notifService, _, _, _, _, _) = CreateServices(nameof(MarkAsRead_UpdatesNotification));

        var dispatched = await notifService.DispatchNotificationAsync(new DirectNotificationRequest(
            _riderUuid, RecipientRole.RIDER, "ALERT", NotificationCategory.SYSTEM,
            "Read Test", "Body", null, null, null, null, null
        ));

        var updated = await notifService.MarkAsReadAsync(_riderUuid, dispatched.NotificationUuid);
        Assert.True(updated.IsRead);
        Assert.NotNull(updated.ReadAt);
    }

    [Fact]
    public async Task MarkAllAsRead_UpdatesAllUserNotifications()
    {
        var (notifService, _, _, _, _, _) = CreateServices(nameof(MarkAllAsRead_UpdatesAllUserNotifications));

        for (int i = 1; i <= 3; i++)
        {
            await notifService.DispatchNotificationAsync(new DirectNotificationRequest(
                _riderUuid, RecipientRole.RIDER, "TEST", NotificationCategory.SYSTEM,
                $"Bulk {i}", "Bulk message", null, null, null, null, null
            ));
        }

        var count = await notifService.MarkAllAsReadAsync(_riderUuid);
        Assert.Equal(3, count);

        var unread = await notifService.GetUnreadCountAsync(_riderUuid);
        Assert.Equal(0, unread.UnreadCount);
    }

    [Fact]
    public async Task RegisterDevice_ReturnsMaskedToken()
    {
        var (_, deviceService, _, _, _, _) = CreateServices(nameof(RegisterDevice_ReturnsMaskedToken));

        var req = new DeviceRegistrationRequest(
            DevicePlatform.ANDROID,
            "fcm_token_super_secret_unique_string_123456789",
            "Pixel 8 Pro",
            "Android 14",
            "1.0.0"
        );

        var res = await deviceService.RegisterDeviceAsync(_riderUuid, req);
        Assert.NotNull(res);
        Assert.Equal("fcm_...6789", res.MaskedToken);
        Assert.Equal(DevicePlatform.ANDROID, res.Platform);
    }

    [Fact]
    public async Task DeactivateDevice_DeactivatesDevice()
    {
        var (_, deviceService, _, _, _, _) = CreateServices(nameof(DeactivateDevice_DeactivatesDevice));

        var reg = await deviceService.RegisterDeviceAsync(_riderUuid, new DeviceRegistrationRequest(
            DevicePlatform.IOS, "fcm_ios_token_123456789", null, null, null
        ));

        await deviceService.DeactivateDeviceAsync(_riderUuid, reg.DeviceUuid);

        var devices = await deviceService.ListUserDevicesAsync(_riderUuid);
        Assert.Empty(devices);
    }

    [Fact]
    public async Task UpdatePreferences_EnforcesSecurityAlerts()
    {
        var (_, _, prefService, _, _, _) = CreateServices(nameof(UpdatePreferences_EnforcesSecurityAlerts));

        var req = new UpdatePreferencesRequest(
            false, false, false, true, true, false, false, null, null, "hi"
        );

        var updated = await prefService.UpdatePreferencesAsync(_riderUuid, req);
        Assert.False(updated.PushEnabled);
        Assert.Equal("hi", updated.PreferredLocale);
        // Security alerts must stay true
        Assert.True(updated.SecurityAlertsEnabled);
    }

    [Fact]
    public async Task IngestBusinessEvent_IdempotentDeduplication()
    {
        var (_, _, _, _, eventService, _) = CreateServices(nameof(IngestBusinessEvent_IdempotentDeduplication));

        var eventId = "EVT-" + Guid.NewGuid();
        var req = new BusinessEventRequest(
            eventId,
            "DriverAssigned",
            "BookingService",
            _riderUuid,
            "RIDER",
            "BOOKING",
            "bk_123",
            new Dictionary<string, object> { ["driverName"] = "Rajesh Kumar" }
        );

        // 1st time succeeds
        var res = await eventService.IngestEventAsync(req);
        Assert.Equal("Driver Assigned", res.Title);
        Assert.Contains("Rajesh Kumar", res.Body);

        // 2nd time throws DuplicateEventException
        await Assert.ThrowsAsync<DuplicateEventException>(() => eventService.IngestEventAsync(req));
    }

    [Fact]
    public void DeliverOtp_EnforcesRateLimiting()
    {
        var (_, _, _, otpService, _, _) = CreateServices(nameof(DeliverOtp_EnforcesRateLimiting));

        var req = new OtpDeliveryRequest(
            "+919876543210",
            null,
            "458921",
            "AUTH_LOGIN",
            300
        );

        // 1st request succeeds
        var res1 = otpService.DeliverOtp(req);
        Assert.True(res1.Success);

        // 2nd request throws RateLimitExceededException
        Assert.Throws<RateLimitExceededException>(() => otpService.DeliverOtp(req));
    }

    [Fact]
    public async Task RiderCannotAccessOtherRiderNotification()
    {
        var (notifService, _, _, _, _, _) = CreateServices(nameof(RiderCannotAccessOtherRiderNotification));

        var req = new DirectNotificationRequest(
            _otherRiderUuid,
            RecipientRole.RIDER,
            "PRIVATE_ALERT",
            NotificationCategory.SECURITY,
            "Private",
            "Private Message",
            null, null, null, null, null
        );

        var res = await notifService.DispatchNotificationAsync(req);

        // Rider tries to read Other Rider's notification
        await Assert.ThrowsAsync<ForbiddenException>(() =>
            notifService.GetNotificationAsync(_riderUuid, res.NotificationUuid));
    }

    [Fact]
    public async Task AdminMetricsAndAudit()
    {
        var (notifService, _, _, _, _, _) = CreateServices(nameof(AdminMetricsAndAudit));

        var dispatched = await notifService.DispatchNotificationAsync(new DirectNotificationRequest(
            _riderUuid, RecipientRole.RIDER, "PAYMENT_SUCCEEDED", NotificationCategory.PAYMENT,
            "Paid", "Payment confirmed", null, null, null, null, [NotificationChannel.IN_APP]
        ));

        var audit = await notifService.AdminGetDeliveryAuditAsync(dispatched.NotificationUuid);
        Assert.NotEmpty(audit);

        var metrics = await notifService.AdminGetMetricsAsync();
        Assert.True(metrics.TotalNotifications >= 1);
    }

    [Fact]
    public async Task MultilingualTemplateRendering_FallbackToEnglish()
    {
        var (_, _, _, _, _, tplService) = CreateServices(nameof(MultilingualTemplateRendering_FallbackToEnglish));

        // Hindi requested
        var hindi = await tplService.RenderAsync("DRIVER_ASSIGNED", NotificationChannel.PUSH, "hi",
            new Dictionary<string, object> { ["driverName"] = "Vikram" });
        Assert.Equal("चालक नियुक्त!", hindi.Title);
        Assert.Contains("Vikram", hindi.Body);

        // Marathi requested (not in db, falls back to English)
        var marathiFallback = await tplService.RenderAsync("DRIVER_ASSIGNED", NotificationChannel.PUSH, "mr",
            new Dictionary<string, object> { ["driverName"] = "Vikram" });
        Assert.Equal("Driver Assigned!", marathiFallback.Title);
        Assert.Contains("Vikram", marathiFallback.Body);
    }

    [Fact]
    public void OtpDelivery_ToString_RedactsPlaintextOtp()
    {
        var req = new OtpDeliveryRequest(
            "+919876543210",
            "rider@lexor.com",
            "999999",
            "AUTH_LOGIN"
        );

        var str = req.ToString();
        Assert.DoesNotContain("999999", str);
        Assert.Contains("[REDACTED]", str);
    }
}
