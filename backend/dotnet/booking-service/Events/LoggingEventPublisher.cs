using System.Text.Json;
using BookingService.Configuration;
using Microsoft.Extensions.Options;

namespace BookingService.Events;

public class LoggingEventPublisher : IEventPublisher
{
    private readonly ILogger<LoggingEventPublisher> _logger;
    private readonly IHttpClientFactory? _httpClientFactory;
    private readonly BookingSettings? _bookingSettings;

    public LoggingEventPublisher(
        ILogger<LoggingEventPublisher> logger,
        IHttpClientFactory? httpClientFactory = null,
        IOptions<BookingSettings>? bookingSettings = null)
    {
        _logger = logger;
        _httpClientFactory = httpClientFactory;
        _bookingSettings = bookingSettings?.Value;
    }

    public async Task PublishAsync(BookingEvent bookingEvent)
    {
        _logger.LogInformation("Booking Domain Event published: {EventType} for Booking {BookingUuid} -> Payload: {Payload}",
            bookingEvent.EventType,
            bookingEvent.BookingUuid,
            JsonSerializer.Serialize(bookingEvent));

        if (_httpClientFactory != null && _bookingSettings != null && !string.IsNullOrWhiteSpace(_bookingSettings.NotificationServiceUrl))
        {
            try
            {
                var client = _httpClientFactory.CreateClient();
                var payload = new
                {
                    eventId = Guid.NewGuid().ToString(),
                    eventType = bookingEvent.EventType,
                    sourceService = "booking-service",
                    recipientUuid = !string.IsNullOrWhiteSpace(bookingEvent.DriverUserUuid) ? bookingEvent.DriverUserUuid : (bookingEvent.RiderUserUuid ?? "SYSTEM_BROADCAST"),
                    recipientRole = !string.IsNullOrWhiteSpace(bookingEvent.DriverUserUuid) ? "DRIVER" : "RIDER",
                    relatedEntityType = "BOOKING",
                    relatedEntityUuid = bookingEvent.BookingUuid,
                    metadata = bookingEvent.Metadata ?? new Dictionary<string, object>()
                };

                var content = new StringContent(JsonSerializer.Serialize(payload), System.Text.Encoding.UTF8, "application/json");
                await client.PostAsync($"{_bookingSettings.NotificationServiceUrl}/api/v1/internal/notifications/events", content);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Non-fatal: failed to forward business event {EventType} to notification-service", bookingEvent.EventType);
            }
        }
    }
}
