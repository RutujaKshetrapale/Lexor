namespace DriverMatchingService.Events;

public interface IEventPublisher
{
    void Publish(MatchingEvent @event);
}
