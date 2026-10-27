namespace NotificationService.Providers;

public interface ISmsNotificationProvider
{
    string ProviderName { get; }
    bool IsLiveMode { get; }
    ProviderDispatchResult SendSms(string destinationPhone, string messageBody);
}
