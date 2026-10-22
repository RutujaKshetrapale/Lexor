using System.Text.Json;

namespace AdminOperationsService.Clients;

public record PaymentReconciliationResult(
    decimal Collections,
    decimal Refunds,
    decimal Commission,
    long Count,
    bool IsAvailable,
    string? ErrorMessage
);

public class PaymentAdminClient
{
    private readonly HttpClient _httpClient;
    private readonly IConfiguration _config;
    private readonly ILogger<PaymentAdminClient> _logger;

    public PaymentAdminClient(HttpClient httpClient, IConfiguration config, ILogger<PaymentAdminClient> logger)
    {
        _httpClient = httpClient;
        _config = config;
        _logger = logger;
    }

    private string GetInternalApiKey()
    {
        return Environment.GetEnvironmentVariable("INTERNAL_SERVICE_API_KEY")
            ?? Environment.GetEnvironmentVariable("LEXOR_INTERNAL_SERVICE_KEY")
            ?? _config["InternalService:ApiKey"]
            ?? string.Empty;
    }

    public async Task<PaymentReconciliationResult> GetFinancialReconciliationAsync()
    {
        try
        {
            var baseUrl = _config["Services:PaymentServiceUrl"] ?? "http://localhost:8098";
            var apiKey = GetInternalApiKey();

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/payments/reconciliation");
            if (!string.IsNullOrWhiteSpace(apiKey))
            {
                request.Headers.Add("X-Internal-Service-Key", apiKey);
            }

            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            var response = await _httpClient.SendAsync(request, cts.Token);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                decimal collections = root.TryGetProperty("totalCollectedAmount", out var ca) && ca.TryGetDecimal(out var cVal) ? cVal : 0;
                decimal refunds = root.TryGetProperty("totalRefundsDisbursed", out var rd) && rd.TryGetDecimal(out var rVal) ? rVal : 0;
                decimal commission = root.TryGetProperty("totalCommissionEarned", out var ce) && ce.TryGetDecimal(out var cmVal) ? cmVal : 0;
                long count = root.TryGetProperty("totalPaymentsCount", out var tc) && tc.TryGetInt64(out var cnt) ? cnt : 0;

                return new PaymentReconciliationResult(collections, refunds, commission, count, true, null);
            }

            _logger.LogWarning("Payment service returned HTTP {StatusCode}", response.StatusCode);
            return new PaymentReconciliationResult(0, 0, 0, 0, false, $"HTTP {(int)response.StatusCode}");
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query payment reconciliation: {Message}", ex.Message);
            return new PaymentReconciliationResult(0, 0, 0, 0, false, ex.Message);
        }
    }
}
