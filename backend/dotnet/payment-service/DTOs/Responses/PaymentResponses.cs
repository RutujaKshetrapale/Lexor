using PaymentService.Enums;

namespace PaymentService.DTOs.Responses;

public class PaymentResponse
{
    public string PaymentUuid { get; set; } = string.Empty;
    public string BookingUuid { get; set; } = string.Empty;
    public string RiderUuid { get; set; } = string.Empty;
    public string? DriverUuid { get; set; }
    public decimal Amount { get; set; }
    public long AmountPaise { get; set; }
    public string Currency { get; set; } = "INR";
    public PaymentMethod PaymentMethod { get; set; }
    public PaymentStatus Status { get; set; }
    public string Provider { get; set; } = "RAZORPAY";
    public string? ProviderOrderId { get; set; }
    public string? ProviderPaymentId { get; set; }
    public IDictionary<string, object>? CheckoutPayload { get; set; }
    public string? FailureReason { get; set; }
    public DateTime? AuthorizedAt { get; set; }
    public DateTime? CapturedAt { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}

public class PaymentMethodDto
{
    public PaymentMethod Method { get; set; }
    public string DisplayName { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public bool Enabled { get; set; }
    public bool RequiresProviderCheckout { get; set; }
}

public class PaymentReceiptResponse
{
    public string ReceiptNumber { get; set; } = string.Empty;
    public string PaymentUuid { get; set; } = string.Empty;
    public string BookingUuid { get; set; } = string.Empty;
    public DateTime? PaidAt { get; set; }
    public string RiderName { get; set; } = string.Empty;
    public string DriverName { get; set; } = string.Empty;
    public string VehicleModel { get; set; } = string.Empty;
    public string LicensePlate { get; set; } = string.Empty;
    public string PickupAddress { get; set; } = string.Empty;
    public string DropAddress { get; set; } = string.Empty;
    public PaymentMethod PaymentMethod { get; set; }
    public PaymentStatus Status { get; set; }
    public FareBreakdownDto FareBreakdown { get; set; } = new();
    public decimal AmountPaid { get; set; }
    public string Currency { get; set; } = "INR";
}

public class FareBreakdownDto
{
    public decimal BaseFare { get; set; }
    public decimal DistanceFare { get; set; }
    public decimal TimeFare { get; set; }
    public decimal TaxAmount { get; set; }
    public decimal TotalFare { get; set; }
    public decimal PlatformCommission { get; set; }
    public decimal DriverEarnings { get; set; }
}

public class RefundResponse
{
    public string RefundUuid { get; set; } = string.Empty;
    public string PaymentUuid { get; set; } = string.Empty;
    public decimal Amount { get; set; }
    public string Currency { get; set; } = "INR";
    public RefundStatus Status { get; set; }
    public string Reason { get; set; } = string.Empty;
    public string? ProviderRefundId { get; set; }
    public string InitiatedBy { get; set; } = "RIDER";
    public DateTime? ProcessedAt { get; set; }
    public DateTime CreatedAt { get; set; }
}

public class ReconciliationResponse
{
    public long TotalPaymentsCount { get; set; }
    public decimal TotalCollectedAmount { get; set; }
    public decimal TotalCashCollected { get; set; }
    public decimal TotalDigitalCollected { get; set; }
    public decimal TotalCommissionEarned { get; set; }
    public decimal TotalDriverEarnings { get; set; }
    public decimal TotalRefundsDisbursed { get; set; }
    public long DiscrepanciesCount { get; set; }
    public string Status { get; set; } = "HEALTHY";
    public DateTime GeneratedAt { get; set; } = DateTime.UtcNow;
}
