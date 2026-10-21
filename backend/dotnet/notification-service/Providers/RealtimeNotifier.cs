using System.Collections.Concurrent;
using System.Threading.Channels;
using NotificationService.DTOs;

namespace NotificationService.Providers;

public interface IRealtimeNotifier
{
    ChannelReader<NotificationDto> Subscribe(string userUuid);
    void NotifyUser(string userUuid, NotificationDto notification);
}

public class RealtimeNotifier : IRealtimeNotifier
{
    private readonly ConcurrentDictionary<string, Channel<NotificationDto>> _userChannels = new();
    private readonly ILogger<RealtimeNotifier> _logger;

    public RealtimeNotifier(ILogger<RealtimeNotifier> logger)
    {
        _logger = logger;
    }

    public ChannelReader<NotificationDto> Subscribe(string userUuid)
    {
        var channel = Channel.CreateUnbounded<NotificationDto>(new UnboundedChannelOptions
        {
            SingleReader = true,
            SingleWriter = false
        });

        _userChannels[userUuid] = channel;
        _logger.LogDebug("User {UserUuid} subscribed to real-time notification stream", userUuid);
        return channel.Reader;
    }

    public void NotifyUser(string userUuid, NotificationDto notification)
    {
        if (_userChannels.TryGetValue(userUuid, out var channel))
        {
            channel.Writer.TryWrite(notification);
            _logger.LogDebug("Forwarded notification {Uuid} to user {UserUuid} real-time channel",
                notification.NotificationUuid, userUuid);
        }
    }
}
