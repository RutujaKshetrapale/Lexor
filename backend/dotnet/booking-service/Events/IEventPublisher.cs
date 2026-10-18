namespace BookingService.Events;

public interface IEventPublisher
{
    Task PublishAsync(BookingEvent bookingEvent);
}
