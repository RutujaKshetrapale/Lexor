using System.Text.Json;

namespace AdminOperationsService.Clients;

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

    public async Task<(decimal collections, decimal refunds, decimal commission, long count)> GetFinancialReconciliationAsync()
    {
        try
        {
            var baseUrl = _config["Services:PaymentServiceUrl"] ?? "http://localhost:8098";
            var apiKey = _config["InternalService:ApiKey"] ?? "lexor-internal-service-secret-key-2026";

            using var request = new HttpRequestMessage(HttpMethod.Get, $"{baseUrl}/api/v1/admin/payments/reconciliation");
            request.Headers.Add("X-Internal-Service-Key", apiKey);

            var response = await _httpClient.SendAsync(request);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                decimal collections = root.TryGetProperty("totalCollectedAmount", out var ca) && ca.TryGetDecimal(out var cVal) ? cVal : 0;
                decimal refunds = root.TryGetProperty("totalRefundsDisbursed", out var rd) && rd.TryGetDecimal(out var rVal) ? rVal : 0;
                decimal commission = root.TryGetProperty("totalCommissionEarned", out var ce) && ce.TryGetDecimal(out var cmVal) ? cmVal : 0;
                long count = root.TryGetProperty("totalPaymentsCount", out var tc) && tc.TryGetInt64(out var cnt) ? cnt : 0;

                return (collections, refunds, commission, count);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning("Failed to query payment reconciliation: {Message}", ex.Message);
        }

        return (0, 0, 0, 0);
    }
}
