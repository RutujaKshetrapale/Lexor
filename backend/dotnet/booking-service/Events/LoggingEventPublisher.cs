using System.Text.Json;

namespace BookingService.Events;

public class LoggingEventPublisher : IEventPublisher
{
    private readonly ILogger<LoggingEventPublisher> _logger;

    public LoggingEventPublisher(ILogger<LoggingEventPublisher> logger)
    {
        _logger = logger;
    }

    public Task PublishAsync(BookingEvent bookingEvent)
    {
        _logger.LogInformation("Booking Domain Event published: {EventType} for Booking {BookingUuid} -> Payload: {Payload}",
            bookingEvent.EventType,
            bookingEvent.BookingUuid,
            JsonSerializer.Serialize(bookingEvent));

        return Task.CompletedTask;
    }
}
