using FeedbackService.DTOs;

namespace FeedbackService.Clients;

public interface INotificationClient
{
    Task SendNotificationAsync(DirectNotificationRequest request);
}
