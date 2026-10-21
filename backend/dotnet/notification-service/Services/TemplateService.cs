using System.Net;
using Microsoft.EntityFrameworkCore;
using NotificationService.Data;
using NotificationService.Domain.Enums;

namespace NotificationService.Services;

public record RenderedTemplate(string Title, string Body);

public interface ITemplateService
{
    Task<RenderedTemplate> RenderAsync(
        string templateCode,
        NotificationChannel channel,
        string? preferredLocale,
        Dictionary<string, object>? variables
    );
}

public class TemplateService : ITemplateService
{
    private readonly NotificationDbContext _dbContext;

    public TemplateService(NotificationDbContext dbContext)
    {
        _dbContext = dbContext;
    }

    public async Task<RenderedTemplate> RenderAsync(
        string templateCode,
        NotificationChannel channel,
        string? preferredLocale,
        Dictionary<string, object>? variables
    )
    {
        var locale = !string.IsNullOrWhiteSpace(preferredLocale) ? preferredLocale.ToLower().Trim() : "en";

        var template = await _dbContext.NotificationTemplates
            .FirstOrDefaultAsync(t => t.TemplateCode == templateCode && t.Channel == channel && t.Locale == locale && t.IsActive);

        if (template == null && locale != "en")
        {
            template = await _dbContext.NotificationTemplates
                .FirstOrDefaultAsync(t => t.TemplateCode == templateCode && t.Channel == channel && t.Locale == "en" && t.IsActive);
        }

        string rawTitle;
        string rawBody;

        if (template != null)
        {
            rawTitle = template.TitleTemplate;
            rawBody = template.BodyTemplate;
        }
        else
        {
            rawTitle = GenerateDefaultTitle(templateCode);
            rawBody = GenerateDefaultBody(templateCode, variables);
        }

        var title = Interpolate(rawTitle, variables, false);
        var body = Interpolate(rawBody, variables, channel == NotificationChannel.EMAIL);

        return new RenderedTemplate(title, body);
    }

    private static string Interpolate(string text, Dictionary<string, object>? variables, bool escapeHtml)
    {
        if (string.IsNullOrEmpty(text) || variables == null || variables.Count == 0) return text ?? string.Empty;

        var result = text;
        foreach (var (key, value) in variables)
        {
            var placeholder = "{" + key + "}";
            var valStr = value?.ToString() ?? string.Empty;
            if (escapeHtml)
            {
                valStr = WebUtility.HtmlEncode(valStr);
            }
            result = result.Replace(placeholder, valStr);
        }
        return result;
    }

    private static string GenerateDefaultTitle(string templateCode) => templateCode switch
    {
        "DRIVER_ASSIGNED" => "Driver Assigned",
        "DRIVER_ARRIVED" => "Driver Arrived",
        "RIDE_STARTED" => "Ride Started",
        "RIDE_COMPLETED" => "Ride Completed",
        "RIDE_CANCELLED" => "Ride Cancelled",
        "PAYMENT_SUCCEEDED" => "Payment Successful",
        "PAYMENT_FAILED" => "Payment Failed",
        "REFUND_COMPLETED" => "Refund Processed",
        "SECURITY_ALERT" => "Security Alert",
        "DRIVER_OFFER" => "New Ride Offer",
        _ => "LEXOR Alert"
    };

    private static string GenerateDefaultBody(string templateCode, Dictionary<string, object>? variables)
    {
        if (variables != null && variables.TryGetValue("message", out var msg))
        {
            return msg?.ToString() ?? "You have an update regarding your LEXOR mobility account.";
        }
        return "You have an update regarding your LEXOR mobility account.";
    }
}
