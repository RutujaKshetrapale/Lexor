using System.Text;
using System.Text.Json;
using FeedbackService.Configuration;
using FeedbackService.DTOs;
using Microsoft.Extensions.Options;

namespace FeedbackService.Clients;

public class NotificationClient : INotificationClient
{
    private readonly HttpClient _httpClient;
    private readonly FeedbackSettings _settings;
    private readonly ILogger<NotificationClient> _logger;

    public NotificationClient(
        HttpClient httpClient,
        IOptions<FeedbackSettings> settings,
        ILogger<NotificationClient> logger)
    {
        _httpClient = httpClient;
        _settings = settings.Value;
        _logger = logger;
    }

    public async Task SendNotificationAsync(DirectNotificationRequest request)
    {
        if (request == null) return;

        try
        {
            var url = $"{_settings.NotificationServiceUrl}/api/v1/internal/notifications/dispatch";
            var json = JsonSerializer.Serialize(request, new JsonSerializerOptions
            {
                PropertyNamingPolicy = JsonNamingPolicy.CamelCase
            });

            using var message = new HttpRequestMessage(HttpMethod.Post, url);
            message.Headers.Add("X-Internal-Service-Key", _settings.InternalServiceKey);
            message.Content = new StringContent(json, Encoding.UTF8, "application/json");

            var response = await _httpClient.SendAsync(message);
            _logger.LogInformation("Dispatched notification to {Recipient} with status {Status}",
                request.RecipientUuid, response.StatusCode);
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Non-fatal: Failed to dispatch notification via {Url}: {Message}",
                _settings.NotificationServiceUrl, ex.Message);
        }
    }
}
