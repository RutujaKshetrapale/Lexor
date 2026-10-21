using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Moq;
using PaymentService.Clients;
using PaymentService.Clients.DTOs;
using PaymentService.Configuration;
using PaymentService.DTOs.Requests;
using PaymentService.Entities;
using PaymentService.Enums;
using PaymentService.Exceptions;
using PaymentService.Providers;
using PaymentService.Services;
using Xunit;

namespace PaymentService.Tests;

public class PaymentServiceIntegrationTests
{
    private readonly PaymentSettings _paymentSettings;
    private readonly Mock<IBookingClient> _bookingClientMock = new();

    private readonly string _riderUuid = "22222222-2222-4222-8222-222222222222";
    private readonly string _driverUuid = "33333333-3333-4333-8333-333333333333";
    private readonly string _adminUuid = "11111111-1111-4111-8111-111111111111";
    private readonly string _bookingUuid = "55555555-5555-4555-8555-555555555555";

    public PaymentServiceIntegrationTests()
    {
        _paymentSettings = new PaymentSettings
        {
            Provider = "RAZORPAY",
            CommissionPercentage = 20.00m,
            Currency = "INR",
            Razorpay = new RazorpaySettings
            {
                KeyId = "rzp_test_sample_key_123456",
                KeySecret = "sample_razorpay_secret_654321",
                WebhookSecret = "sample_webhook_secret_987654"
            }
        };

        var mockBooking = new BookingDetailDto
        {
            BookingUuid = _bookingUuid,
            Status = "CONFIRMED",
            RiderUuid = _riderUuid,
            RiderName = "Rajesh Kumar",
            DriverUuid = _driverUuid,
            DriverName = "Vikram Singh",
            VehicleModel = "Toyota Camry",
            LicensePlate = "KA01AB1234",
            TotalFare = 250.00m,
            Currency = "INR",
            PickupAddress = "Indiranagar, Bengaluru",
            DropAddress = "Prestige Tech Park, Bengaluru",
            BaseFare = 50.00m,
            DistanceFare = 150.00m,
            TimeFare = 30.00m,
            TaxAmount = 20.00m
        };

        _bookingClientMock.Setup(b => b.GetBookingDetailsAsync(_bookingUuid, It.IsAny<string?>()))
            .ReturnsAsync(mockBooking);
    }

    private ClaimsPrincipal CreatePrincipal(string uuid, string role)
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, uuid),
            new("sub", uuid),
            new(ClaimTypes.Role, role),
            new("role", role)
        };
        return new ClaimsPrincipal(new ClaimsIdentity(claims, "TestAuth"));
    }

    private (Services.PaymentService service, Data.PaymentDbContext dbContext) CreateService(string dbName)
    {
        var db = TestDbContextFactory.Create(dbName);
        var provider = new RazorpayPaymentProvider(Options.Create(_paymentSettings), NullLogger<RazorpayPaymentProvider>.Instance);
        var ledger = new LedgerService(db, NullLogger<LedgerService>.Instance);
        var idempotency = new IdempotencyService(db, NullLogger<IdempotencyService>.Instance);

        var service = new Services.PaymentService(
            db,
            provider,
            _bookingClientMock.Object,
            ledger,
            idempotency,
            Options.Create(_paymentSettings),
            NullLogger<Services.PaymentService>.Instance
        );

        return (service, db);
    }

    [Fact]
    public void GetPaymentMethods_ReturnsSupportedMethods()
    {
        var (service, _) = CreateService(nameof(GetPaymentMethods_ReturnsSupportedMethods));
        var methods = service.GetEnabledPaymentMethods();

        Assert.Equal(4, methods.Count);
        Assert.Contains(methods, m => m.Method == PaymentMethod.UPI && m.RequiresProviderCheckout);
        Assert.Contains(methods, m => m.Method == PaymentMethod.CASH && !m.RequiresProviderCheckout);
    }

    [Fact]
    public async Task InitiatePayment_UpiMethod_CreatesPendingPaymentAndRazorpayOrder()
    {
        var (service, db) = CreateService(nameof(InitiatePayment_UpiMethod_CreatesPendingPaymentAndRazorpayOrder));
        var rider = CreatePrincipal(_riderUuid, "RIDER");

        var request = new InitiatePaymentRequest
        {
            BookingUuid = _bookingUuid,
            PaymentMethod = PaymentMethod.UPI
        };

        var response = await service.InitiatePaymentAsync(rider, request, "IDEM-1", null);

        Assert.NotNull(response.PaymentUuid);
        Assert.Equal(_bookingUuid, response.BookingUuid);
        Assert.Equal(250.00m, response.Amount);
        Assert.Equal(PaymentMethod.UPI, response.PaymentMethod);
        Assert.Equal(PaymentStatus.PENDING, response.Status);
        Assert.NotNull(response.ProviderOrderId);
        Assert.NotNull(response.CheckoutPayload);

        var saved = db.Payments.FirstOrDefault(p => p.PaymentUuid == response.PaymentUuid);
        Assert.NotNull(saved);
        Assert.Equal(PaymentStatus.PENDING, saved.Status);
    }

    [Fact]
    public async Task InitiatePayment_CashMethod_CreatesPendingPaymentWithoutGatewayOrder()
    {
        var (service, db) = CreateService(nameof(InitiatePayment_CashMethod_CreatesPendingPaymentWithoutGatewayOrder));
        var rider = CreatePrincipal(_riderUuid, "RIDER");

        var request = new InitiatePaymentRequest
        {
            BookingUuid = _bookingUuid,
            PaymentMethod = PaymentMethod.CASH
        };

        var response = await service.InitiatePaymentAsync(rider, request, null, null);

        Assert.NotNull(response.PaymentUuid);
        Assert.Equal(PaymentMethod.CASH, response.PaymentMethod);
        Assert.Equal(PaymentStatus.PENDING, response.Status);
        Assert.Null(response.ProviderOrderId);
    }

    [Fact]
    public async Task InitiatePayment_DuplicateIdempotencyKey_ReturnsCachedResponse()
    {
        var (service, db) = CreateService(nameof(InitiatePayment_DuplicateIdempotencyKey_ReturnsCachedResponse));
        var rider = CreatePrincipal(_riderUuid, "RIDER");

        var request = new InitiatePaymentRequest
        {
            BookingUuid = _bookingUuid,
            PaymentMethod = PaymentMethod.UPI
        };

        var res1 = await service.InitiatePaymentAsync(rider, request, "IDEM-DUP-1", null);
        var res2 = await service.InitiatePaymentAsync(rider, request, "IDEM-DUP-1", null);

        Assert.Equal(res1.PaymentUuid, res2.PaymentUuid);
        Assert.Equal(1, db.Payments.Count());
    }

    [Fact]
    public async Task InitiatePayment_OtherRiderBooking_ThrowsForbiddenException()
    {
        var (service, _) = CreateService(nameof(InitiatePayment_OtherRiderBooking_ThrowsForbiddenException));
        var otherRider = CreatePrincipal("other-rider-uuid", "RIDER");

        var request = new InitiatePaymentRequest
        {
            BookingUuid = _bookingUuid,
            PaymentMethod = PaymentMethod.UPI
        };

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            service.InitiatePaymentAsync(otherRider, request, null, null));
    }

    [Fact]
    public async Task GetPaymentReceipt_PendingPayment_ThrowsBadRequestException()
    {
        var (service, db) = CreateService(nameof(GetPaymentReceipt_PendingPayment_ThrowsBadRequestException));
        var rider = CreatePrincipal(_riderUuid, "RIDER");

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.UPI,
            Status = PaymentStatus.PENDING
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        await Assert.ThrowsAsync<BadRequestException>(() =>
            service.GetPaymentReceiptAsync(rider, payment.PaymentUuid, null));
    }

    [Fact]
    public async Task GetPaymentReceipt_SucceededPayment_ReturnsReceiptWithBreakdown()
    {
        var (service, db) = CreateService(nameof(GetPaymentReceipt_SucceededPayment_ReturnsReceiptWithBreakdown));
        var rider = CreatePrincipal(_riderUuid, "RIDER");

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.UPI,
            Status = PaymentStatus.SUCCEEDED,
            CapturedAt = DateTime.UtcNow
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        var receipt = await service.GetPaymentReceiptAsync(rider, payment.PaymentUuid, null);

        Assert.NotNull(receipt.ReceiptNumber);
        Assert.Equal("Rajesh Kumar", receipt.RiderName);
        Assert.Equal(250.00m, receipt.AmountPaid);
        Assert.Equal(50.00m, receipt.FareBreakdown.PlatformCommission);
        Assert.Equal(200.00m, receipt.FareBreakdown.DriverEarnings);
    }

    [Fact]
    public async Task ConfirmCashCollection_AssignedDriver_TransitionsToSucceededAndRecordsLedger()
    {
        var (service, db) = CreateService(nameof(ConfirmCashCollection_AssignedDriver_TransitionsToSucceededAndRecordsLedger));
        var driver = CreatePrincipal(_driverUuid, "DRIVER");

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.CASH,
            Status = PaymentStatus.PENDING
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        var result = await service.ConfirmCashCollectionAsync(driver, payment.PaymentUuid, new CashCollectionRequest { AmountCollected = 250.00m });

        Assert.Equal(PaymentStatus.SUCCEEDED, result.Status);

        var updated = db.Payments.FirstOrDefault(p => p.PaymentUuid == payment.PaymentUuid);
        Assert.NotNull(updated);
        Assert.Equal(PaymentStatus.SUCCEEDED, updated.Status);
        Assert.NotNull(updated.CapturedAt);

        // Verify ledger entries
        Assert.True(db.FinancialLedgerEntries.Count() >= 3);
    }

    [Fact]
    public async Task ConfirmCashCollection_UnassignedDriver_ThrowsForbiddenException()
    {
        var (service, db) = CreateService(nameof(ConfirmCashCollection_UnassignedDriver_ThrowsForbiddenException));
        var otherDriver = CreatePrincipal("unassigned-driver-uuid", "DRIVER");

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.CASH,
            Status = PaymentStatus.PENDING
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        await Assert.ThrowsAsync<ForbiddenException>(() =>
            service.ConfirmCashCollectionAsync(otherDriver, payment.PaymentUuid, null));
    }

    [Fact]
    public async Task ProcessWebhook_ValidSignature_CapturesPaymentAndRecordsLedger()
    {
        var (service, db) = CreateService(nameof(ProcessWebhook_ValidSignature_CapturesPaymentAndRecordsLedger));

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.UPI,
            ProviderOrderId = "order_test_webhook_123",
            Status = PaymentStatus.PENDING
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        var rawPayload = "{\"id\":\"evt_12345\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_12345\",\"order_id\":\"order_test_webhook_123\",\"amount\":25000,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        var validSignature = ComputeHmacSha256(rawPayload, _paymentSettings.Razorpay.WebhookSecret);

        await service.ProcessWebhookAsync("razorpay", rawPayload, validSignature);

        var updated = db.Payments.FirstOrDefault(p => p.PaymentUuid == payment.PaymentUuid);
        Assert.NotNull(updated);
        Assert.Equal(PaymentStatus.SUCCEEDED, updated.Status);
        Assert.Equal("pay_12345", updated.ProviderPaymentId);
    }

    [Fact]
    public async Task ProcessWebhook_InvalidSignature_ThrowsBadRequestException()
    {
        var (service, _) = CreateService(nameof(ProcessWebhook_InvalidSignature_ThrowsBadRequestException));
        var rawPayload = "{\"id\":\"evt_bad\",\"event\":\"payment.captured\"}";

        await Assert.ThrowsAsync<BadRequestException>(() =>
            service.ProcessWebhookAsync("razorpay", rawPayload, "invalid_signature"));
    }

    [Fact]
    public async Task RequestRefund_SucceededPayment_ProcessesRefundAndRecordsLedger()
    {
        var (service, db) = CreateService(nameof(RequestRefund_SucceededPayment_ProcessesRefundAndRecordsLedger));
        var rider = CreatePrincipal(_riderUuid, "RIDER");

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.UPI,
            ProviderPaymentId = "pay_test_refund_99",
            Status = PaymentStatus.SUCCEEDED
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        var refundResult = await service.RequestRefundAsync(rider, payment.PaymentUuid, new RefundPaymentRequest
        {
            Amount = 250.00m,
            Reason = "Driver did not arrive"
        });

        Assert.Equal(RefundStatus.PROCESSED, refundResult.Status);
        Assert.Equal(250.00m, refundResult.Amount);

        var updated = db.Payments.FirstOrDefault(p => p.PaymentUuid == payment.PaymentUuid);
        Assert.NotNull(updated);
        Assert.Equal(PaymentStatus.REFUNDED, updated.Status);
    }

    [Fact]
    public async Task ProcessAdminRefund_ValidAdmin_ProcessesRefundWithNotes()
    {
        var (service, db) = CreateService(nameof(ProcessAdminRefund_ValidAdmin_ProcessesRefundWithNotes));
        var admin = CreatePrincipal(_adminUuid, "ADMIN");

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = _bookingUuid,
            RiderUuid = _riderUuid,
            DriverUuid = _driverUuid,
            Amount = 250.00m,
            AmountPaise = 25000,
            PaymentMethod = PaymentMethod.UPI,
            ProviderPaymentId = "pay_admin_ref_1",
            Status = PaymentStatus.SUCCEEDED
        };
        db.Payments.Add(payment);
        await db.SaveChangesAsync();

        var res = await service.ProcessAdminRefundAsync(admin, payment.PaymentUuid, new AdminRefundRequest
        {
            Amount = 100.00m,
            Reason = "Courtesy refund",
            AdminNotes = "Approved by Operations Lead"
        });

        Assert.Equal(RefundStatus.PROCESSED, res.Status);
        Assert.Equal(100.00m, res.Amount);

        var report = await service.GetReconciliationReportAsync();
        Assert.Equal("HEALTHY", report.Status);
        Assert.True(report.TotalPaymentsCount >= 1);
    }

    private static string ComputeHmacSha256(string data, string key)
    {
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(key));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(data));
        return Convert.ToHexStringLower(hash);
    }
}
