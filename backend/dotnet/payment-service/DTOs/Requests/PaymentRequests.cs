using System.ComponentModel.DataAnnotations;
using PaymentService.Enums;

namespace PaymentService.DTOs.Requests;

public class InitiatePaymentRequest
{
    [Required(ErrorMessage = "Booking UUID is required")]
    public string BookingUuid { get; set; } = string.Empty;

    [Required(ErrorMessage = "Payment method is required")]
    public PaymentMethod PaymentMethod { get; set; }

    public string? Provider { get; set; }
}

public class RetryPaymentRequest
{
    public PaymentMethod? PaymentMethod { get; set; }
}

public class CashCollectionRequest
{
    public decimal? AmountCollected { get; set; }
    public string? Notes { get; set; }
}

public class RefundPaymentRequest
{
    public decimal? Amount { get; set; }

    [Required(ErrorMessage = "Refund reason is required")]
    public string Reason { get; set; } = string.Empty;
}

public class AdminRefundRequest
{
    [Required(ErrorMessage = "Refund amount is required")]
    [Range(1.00, 1000000.00, ErrorMessage = "Refund amount must be at least 1.00")]
    public decimal Amount { get; set; }

    [Required(ErrorMessage = "Refund reason is required")]
    public string Reason { get; set; } = string.Empty;

    [Required(ErrorMessage = "Admin audit notes are required")]
    public string AdminNotes { get; set; } = string.Empty;
}
