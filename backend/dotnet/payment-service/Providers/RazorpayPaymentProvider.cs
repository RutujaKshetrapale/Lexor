using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Options;
using PaymentService.Configuration;
using PaymentService.Enums;
using PaymentService.Exceptions;
using PaymentService.Providers.DTOs;

namespace PaymentService.Providers;

public interface IPaymentProvider
{
    string ProviderName { get; }
    CreateOrderResult CreateOrder(string bookingUuid, decimal amount, string currency, PaymentMethod method);
    bool VerifyCheckoutSignature(string orderId, string paymentId, string signature);
    bool VerifyWebhookSignature(string rawBody, string signature);
    CaptureResult CapturePayment(string paymentId, decimal amount, string currency);
    RefundResult InitiateRefund(string paymentId, decimal amount, string currency, string reason);
    PaymentVerificationResult FetchPaymentStatus(string paymentId);
}

public class RazorpayPaymentProvider : IPaymentProvider
{
    private readonly PaymentSettings _settings;
    private readonly ILogger<RazorpayPaymentProvider> _logger;

    public RazorpayPaymentProvider(IOptions<PaymentSettings> settings, ILogger<RazorpayPaymentProvider> logger)
    {
        _settings = settings.Value;
        _logger = logger;
    }

    public string ProviderName => "RAZORPAY";

    public CreateOrderResult CreateOrder(string bookingUuid, decimal amount, string currency, PaymentMethod method)
    {
        if (amount <= 0)
        {
            throw new PaymentGatewayException("Invalid transaction amount for order creation");
        }

        var amountPaise = (long)Math.Round(amount * 100, MidpointRounding.AwayFromZero);
        var orderId = "order_" + Guid.NewGuid().ToString("N")[..14];

        var checkoutPayload = new Dictionary<string, object>
        {
            ["orderId"] = orderId,
            ["amount"] = amountPaise,
            ["currency"] = string.IsNullOrWhiteSpace(currency) ? "INR" : currency,
            ["name"] = "LEXOR — Intelligent Mobility",
            ["description"] = $"Ride Payment for Booking {bookingUuid}",
            ["key"] = _settings.Razorpay.KeyId
        };

        if (method == PaymentMethod.UPI)
        {
            checkoutPayload["upi"] = new Dictionary<string, object>
            {
                ["flow"] = "intent",
                ["vpa"] = "lexor@icici",
                ["intentUrl"] = $"upi://pay?pa=lexor@icici&pn=LEXOR&am={amount}&cu=INR&tr={orderId}",
                ["qrCodeString"] = $"upi://pay?pa=lexor@icici&pn=LEXOR&am={amount}&cu=INR&tr={orderId}"
            };
        }
        else if (method == PaymentMethod.CARD)
        {
            checkoutPayload["card"] = new Dictionary<string, object>
            {
                ["saveCardAllowed"] = true,
                ["supportedNetworks"] = new[] { "VISA", "MASTERCARD", "RUPAY" }
            };
        }
        else if (method == PaymentMethod.NET_BANKING)
        {
            checkoutPayload["netbanking"] = new Dictionary<string, object>
            {
                ["supportedBanks"] = new[] { "HDFC", "ICICI", "SBIN", "KKBK", "AXIS" }
            };
        }

        _logger.LogInformation("Created Razorpay sandbox order {OrderId} for booking {BookingUuid} with method {Method}", orderId, bookingUuid, method);

        return new CreateOrderResult
        {
            OrderId = orderId,
            AmountPaise = amountPaise,
            Amount = amount,
            Currency = string.IsNullOrWhiteSpace(currency) ? "INR" : currency,
            ProviderKeyId = _settings.Razorpay.KeyId,
            CheckoutPayload = checkoutPayload
        };
    }

    public bool VerifyCheckoutSignature(string orderId, string paymentId, string signature)
    {
        if (string.IsNullOrWhiteSpace(orderId) || string.IsNullOrWhiteSpace(paymentId) || string.IsNullOrWhiteSpace(signature))
        {
            return false;
        }

        try
        {
            var data = $"{orderId}|{paymentId}";
            var computed = ComputeHmacSha256(data, _settings.Razorpay.KeySecret);
            return CryptographicOperations.FixedTimeEquals(
                Encoding.UTF8.GetBytes(computed),
                Encoding.UTF8.GetBytes(signature)
            );
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error verifying checkout signature");
            return false;
        }
    }

    public bool VerifyWebhookSignature(string rawBody, string signature)
    {
        if (string.IsNullOrWhiteSpace(rawBody) || string.IsNullOrWhiteSpace(signature))
        {
            return false;
        }

        try
        {
            var computed = ComputeHmacSha256(rawBody, _settings.Razorpay.WebhookSecret);
            return CryptographicOperations.FixedTimeEquals(
                Encoding.UTF8.GetBytes(computed),
                Encoding.UTF8.GetBytes(signature)
            );
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error verifying webhook signature");
            return false;
        }
    }

    public CaptureResult CapturePayment(string paymentId, decimal amount, string currency)
    {
        if (string.IsNullOrWhiteSpace(paymentId))
        {
            throw new PaymentGatewayException("Missing provider payment ID for capture");
        }

        _logger.LogInformation("Captured payment {PaymentId} for amount {Amount} {Currency}", paymentId, amount, currency);
        return new CaptureResult
        {
            Successful = true,
            PaymentId = paymentId,
            Amount = amount,
            Currency = string.IsNullOrWhiteSpace(currency) ? "INR" : currency,
            Status = "captured"
        };
    }

    public RefundResult InitiateRefund(string paymentId, decimal amount, string currency, string reason)
    {
        if (string.IsNullOrWhiteSpace(paymentId))
        {
            throw new PaymentGatewayException("Missing provider payment ID for refund");
        }

        var refundId = "rfnd_" + Guid.NewGuid().ToString("N")[..14];
        _logger.LogInformation("Initiated refund {RefundId} for payment {PaymentId} for amount {Amount} {Currency}: {Reason}", refundId, paymentId, amount, currency, reason);

        return new RefundResult
        {
            Successful = true,
            RefundId = refundId,
            PaymentId = paymentId,
            Amount = amount,
            Currency = string.IsNullOrWhiteSpace(currency) ? "INR" : currency,
            Status = "processed"
        };
    }

    public PaymentVerificationResult FetchPaymentStatus(string paymentId)
    {
        return new PaymentVerificationResult
        {
            Verified = true,
            PaymentId = paymentId,
            Status = "captured",
            Method = "upi"
        };
    }

    private static string ComputeHmacSha256(string data, string key)
    {
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(key));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(data));
        return Convert.ToHexStringLower(hash);
    }
}
