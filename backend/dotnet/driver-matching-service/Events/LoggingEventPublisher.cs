namespace DriverMatchingService.Events;

public class LoggingEventPublisher : IEventPublisher
{
    private readonly ILogger<LoggingEventPublisher> _logger;
    private readonly List<MatchingEvent> _publishedEvents = new();
    private readonly object _lock = new();

    public LoggingEventPublisher(ILogger<LoggingEventPublisher> logger)
    {
        _logger = logger;
    }

    public void Publish(MatchingEvent @event)
    {
        lock (_lock)
        {
            _publishedEvents.Add(@event);
        }

        _logger.LogInformation("[EVENT-BUS] Published Event [{EventType}] for RideRequest [{RideRequestUuid}]: EventId={EventId}",
            @event.EventType,
            @event.RideRequestUuid,
            @event.EventId);
    }

    public IReadOnlyList<MatchingEvent> GetPublishedEvents()
    {
        lock (_lock)
        {
            return _publishedEvents.ToList();
        }
    }

    public void ClearEvents()
    {
        lock (_lock)
        {
            _publishedEvents.Clear();
        }
    }
}
