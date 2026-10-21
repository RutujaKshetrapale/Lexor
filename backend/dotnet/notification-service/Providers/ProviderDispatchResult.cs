using NotificationService.Domain.Enums;

namespace NotificationService.Providers;

public class ProviderDispatchResult
{
    public bool Successful { get; set; }
    public DeliveryStatus Status { get; set; }
    public string? ProviderMessageId { get; set; }
    public string? ErrorCode { get; set; }
    public string? ErrorMessage { get; set; }
    public bool ShouldRetry { get; set; }

    public static ProviderDispatchResult Success(string providerMessageId) => new()
    {
        Successful = true,
        Status = DeliveryStatus.DELIVERED,
        ProviderMessageId = providerMessageId
    };

    public static ProviderDispatchResult Accepted(string providerMessageId) => new()
    {
        Successful = true,
        Status = DeliveryStatus.ACCEPTED,
        ProviderMessageId = providerMessageId
    };

    public static ProviderDispatchResult Failure(string errorCode, string errorMessage, bool shouldRetry = false) => new()
    {
        Successful = false,
        Status = shouldRetry ? DeliveryStatus.RETRY_SCHEDULED : DeliveryStatus.FAILED,
        ErrorCode = errorCode,
        ErrorMessage = errorMessage,
        ShouldRetry = shouldRetry
    };
}
