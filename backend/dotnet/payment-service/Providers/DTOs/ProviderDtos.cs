namespace PaymentService.Providers.DTOs;

public class CreateOrderResult
{
    public string OrderId { get; set; } = string.Empty;
    public long AmountPaise { get; set; }
    public decimal Amount { get; set; }
    public string Currency { get; set; } = "INR";
    public string ProviderKeyId { get; set; } = string.Empty;
    public IDictionary<string, object> CheckoutPayload { get; set; } = new Dictionary<string, object>();
}

public class CaptureResult
{
    public bool Successful { get; set; }
    public string PaymentId { get; set; } = string.Empty;
    public string? OrderId { get; set; }
    public decimal Amount { get; set; }
    public string Currency { get; set; } = "INR";
    public string Status { get; set; } = string.Empty;
    public string? FailureReason { get; set; }
}

public class RefundResult
{
    public bool Successful { get; set; }
    public string RefundId { get; set; } = string.Empty;
    public string PaymentId { get; set; } = string.Empty;
    public decimal Amount { get; set; }
    public string Currency { get; set; } = "INR";
    public string Status { get; set; } = string.Empty;
    public string? FailureReason { get; set; }
}

public class PaymentVerificationResult
{
    public bool Verified { get; set; }
    public string PaymentId { get; set; } = string.Empty;
    public string? OrderId { get; set; }
    public decimal Amount { get; set; }
    public string Status { get; set; } = string.Empty;
    public string? Method { get; set; }
}
