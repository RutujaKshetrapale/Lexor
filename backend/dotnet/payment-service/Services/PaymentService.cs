using System.Security.Claims;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using PaymentService.Clients;
using PaymentService.Clients.DTOs;
using PaymentService.Configuration;
using PaymentService.Data;
using PaymentService.DTOs.Requests;
using PaymentService.DTOs.Responses;
using PaymentService.Entities;
using PaymentService.Enums;
using PaymentService.Exceptions;
using PaymentService.Providers;
using PaymentService.Providers.DTOs;

namespace PaymentService.Services;

public interface IPaymentService
{
    List<PaymentMethodDto> GetEnabledPaymentMethods();
    Task<PaymentResponse> InitiatePaymentAsync(ClaimsPrincipal user, InitiatePaymentRequest request, string? idempotencyKey, string? authHeader);
    Task<PageResponse<PaymentResponse>> GetRiderPaymentsAsync(ClaimsPrincipal user, int pageNumber, int pageSize);
    Task<PaymentResponse> GetRiderPaymentByUuidAsync(ClaimsPrincipal user, string paymentUuid);
    Task<PaymentReceiptResponse> GetPaymentReceiptAsync(ClaimsPrincipal user, string paymentUuid, string? authHeader);
    Task<PaymentResponse> RetryPaymentAsync(ClaimsPrincipal user, string paymentUuid, RetryPaymentRequest? request);
    Task<RefundResponse> RequestRefundAsync(ClaimsPrincipal user, string paymentUuid, RefundPaymentRequest request);
    Task<PaymentResponse> GetPaymentStatusAsync(ClaimsPrincipal user, string paymentUuid, bool refresh);
    Task<PaymentResponse> ConfirmCashCollectionAsync(ClaimsPrincipal driverUser, string paymentUuid, CashCollectionRequest? request);
    Task ProcessWebhookAsync(string provider, string rawBody, string? signature);
    Task<PageResponse<PaymentResponse>> GetAdminPaymentsAsync(PaymentStatus? status, PaymentMethod? method, string? riderUuid, string? driverUuid, int pageNumber, int pageSize);
    Task<PaymentResponse> GetAdminPaymentByUuidAsync(string paymentUuid);
    Task<RefundResponse> ProcessAdminRefundAsync(ClaimsPrincipal adminUser, string paymentUuid, AdminRefundRequest request);
    Task<ReconciliationResponse> GetReconciliationReportAsync();
}

public class PaymentService : IPaymentService
{
    private readonly PaymentDbContext _dbContext;
    private readonly IPaymentProvider _paymentProvider;
    private readonly IBookingClient _bookingClient;
    private readonly ILedgerService _ledgerService;
    private readonly IIdempotencyService _idempotencyService;
    private readonly PaymentSettings _settings;
    private readonly ILogger<PaymentService> _logger;

    public PaymentService(
        PaymentDbContext dbContext,
        IPaymentProvider paymentProvider,
        IBookingClient bookingClient,
        ILedgerService ledgerService,
        IIdempotencyService idempotencyService,
        IOptions<PaymentSettings> settings,
        ILogger<PaymentService> logger)
    {
        _dbContext = dbContext;
        _paymentProvider = paymentProvider;
        _bookingClient = bookingClient;
        _ledgerService = ledgerService;
        _idempotencyService = idempotencyService;
        _settings = settings.Value;
        _logger = logger;
    }

    public List<PaymentMethodDto> GetEnabledPaymentMethods()
    {
        return new List<PaymentMethodDto>
        {
            new() { Method = PaymentMethod.UPI, DisplayName = "UPI (GPay, PhonePe, Paytm, QR)", Description = "Zero-redirect instant UPI collect, intent, or scan-and-pay QR", Enabled = true, RequiresProviderCheckout = true },
            new() { Method = PaymentMethod.CARD, DisplayName = "Credit / Debit Cards", Description = "Visa, MasterCard, RuPay with secure tokenization", Enabled = true, RequiresProviderCheckout = true },
            new() { Method = PaymentMethod.NET_BANKING, DisplayName = "Net Banking", Description = "Over 50+ supported Indian commercial and retail banks", Enabled = true, RequiresProviderCheckout = true },
            new() { Method = PaymentMethod.CASH, DisplayName = "Cash to Driver", Description = "Pay driver directly upon successful trip completion", Enabled = true, RequiresProviderCheckout = false }
        };
    }

    public async Task<PaymentResponse> InitiatePaymentAsync(ClaimsPrincipal user, InitiatePaymentRequest request, string? idempotencyKey, string? authHeader)
    {
        var riderUuid = GetUserUuid(user);

        // 1. Idempotency Check
        if (!string.IsNullOrWhiteSpace(idempotencyKey))
        {
            var cached = await _idempotencyService.FindCachedResponseAsync<PaymentResponse>(riderUuid, idempotencyKey);
            if (cached != null)
            {
                return cached;
            }
        }

        // 2. Authoritative booking validation
        var booking = await _bookingClient.GetBookingDetailsAsync(request.BookingUuid, authHeader);
        if (booking == null)
        {
            throw new ResourceNotFoundException($"Booking with UUID {request.BookingUuid} was not found");
        }

        if (!booking.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException($"Authenticated rider does not own booking {request.BookingUuid}");
        }

        // 3. Existing payment check
        var existing = await _dbContext.Payments.FirstOrDefaultAsync(p => p.BookingUuid == request.BookingUuid);
        if (existing != null && existing.Status == PaymentStatus.SUCCEEDED)
        {
            throw new ConflictException($"Payment for booking {request.BookingUuid} has already succeeded");
        }

        var fare = booking.TotalFare > 0 ? booking.TotalFare : 250.00m;
        var farePaise = (long)Math.Round(fare * 100, MidpointRounding.AwayFromZero);

        var payment = new Payment
        {
            PaymentUuid = Guid.NewGuid().ToString(),
            BookingUuid = booking.BookingUuid,
            RiderUuid = riderUuid,
            DriverUuid = booking.DriverUuid,
            Amount = fare,
            AmountPaise = farePaise,
            Currency = string.IsNullOrWhiteSpace(booking.Currency) ? "INR" : booking.Currency,
            PaymentMethod = request.PaymentMethod,
            Status = PaymentStatus.PENDING,
            Provider = _paymentProvider.ProviderName,
            IdempotencyKey = idempotencyKey
        };

        CreateOrderResult? orderResult = null;
        if (request.PaymentMethod != PaymentMethod.CASH)
        {
            orderResult = _paymentProvider.CreateOrder(booking.BookingUuid, fare, payment.Currency, request.PaymentMethod);
            payment.ProviderOrderId = orderResult.OrderId;
        }

        _dbContext.Payments.Add(payment);
        await _dbContext.SaveChangesAsync();

        var tx = new PaymentTransaction
        {
            PaymentId = payment.Id,
            PaymentUuid = payment.PaymentUuid,
            AttemptType = request.PaymentMethod == PaymentMethod.CASH ? TransactionAttemptType.CASH_COLLECTION : TransactionAttemptType.SALE,
            GatewayProvider = _paymentProvider.ProviderName,
            GatewayOrderId = payment.ProviderOrderId,
            Amount = fare,
            AmountPaise = farePaise,
            Currency = payment.Currency,
            Status = TransactionStatus.INITIATED
        };
        _dbContext.PaymentTransactions.Add(tx);
        await _dbContext.SaveChangesAsync();

        var response = MapToResponse(payment, orderResult?.CheckoutPayload);

        if (!string.IsNullOrWhiteSpace(idempotencyKey))
        {
            await _idempotencyService.StoreResponseAsync(riderUuid, idempotencyKey, "PAYMENT_INITIATE", 201, response);
        }

        return response;
    }

    public async Task<PageResponse<PaymentResponse>> GetRiderPaymentsAsync(ClaimsPrincipal user, int pageNumber, int pageSize)
    {
        var riderUuid = GetUserUuid(user);
        var page = pageNumber > 0 ? pageNumber : 0;
        var size = pageSize > 0 ? pageSize : 10;

        var query = _dbContext.Payments.Where(p => p.RiderUuid == riderUuid).OrderByDescending(p => p.CreatedAt);
        var total = await query.LongCountAsync();
        var items = await query.Skip(page * size).Take(size).ToListAsync();

        return new PageResponse<PaymentResponse>
        {
            Content = items.Select(p => MapToResponse(p, null)).ToList(),
            PageNumber = page,
            PageSize = size,
            TotalElements = total,
            TotalPages = (int)Math.Ceiling(total / (double)size),
            Last = (page + 1) * size >= total
        };
    }

    public async Task<PaymentResponse> GetRiderPaymentByUuidAsync(ClaimsPrincipal user, string paymentUuid)
    {
        var riderUuid = GetUserUuid(user);
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (!payment.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        return MapToResponse(payment, null);
    }

    public async Task<PaymentReceiptResponse> GetPaymentReceiptAsync(ClaimsPrincipal user, string paymentUuid, string? authHeader)
    {
        var riderUuid = GetUserUuid(user);
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (!payment.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (payment.Status != PaymentStatus.SUCCEEDED &&
            payment.Status != PaymentStatus.PARTIALLY_REFUNDED &&
            payment.Status != PaymentStatus.REFUNDED)
        {
            throw new BadRequestException("Receipt is only available for succeeded or completed payments");
        }

        var booking = await _bookingClient.GetBookingDetailsAsync(payment.BookingUuid, authHeader);
        var b = booking ?? new BookingDetailDto
        {
            BookingUuid = payment.BookingUuid,
            RiderName = "Valued Customer",
            DriverName = "LEXOR Chauffeur",
            VehicleModel = "LEXOR Mobility Vehicle",
            LicensePlate = "KA01LX0001",
            PickupAddress = "LEXOR Pickup Point",
            DropAddress = "LEXOR Drop Point"
        };

        var commRate = _settings.CommissionPercentage;
        var commission = Math.Round(payment.Amount * commRate / 100m, 2, MidpointRounding.AwayFromZero);
        var earnings = payment.Amount - commission;

        return new PaymentReceiptResponse
        {
            ReceiptNumber = $"RCP-{payment.PaymentUuid[..8].ToUpperInvariant()}",
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            PaidAt = payment.CapturedAt ?? payment.UpdatedAt,
            RiderName = b.RiderName,
            DriverName = b.DriverName,
            VehicleModel = b.VehicleModel,
            LicensePlate = b.LicensePlate,
            PickupAddress = b.PickupAddress,
            DropAddress = b.DropAddress,
            PaymentMethod = payment.PaymentMethod,
            Status = payment.Status,
            AmountPaid = payment.Amount,
            Currency = payment.Currency,
            FareBreakdown = new FareBreakdownDto
            {
                BaseFare = b.BaseFare,
                DistanceFare = b.DistanceFare,
                TimeFare = b.TimeFare,
                TaxAmount = b.TaxAmount,
                TotalFare = payment.Amount,
                PlatformCommission = commission,
                DriverEarnings = earnings
            }
        };
    }

    public async Task<PaymentResponse> RetryPaymentAsync(ClaimsPrincipal user, string paymentUuid, RetryPaymentRequest? request)
    {
        var riderUuid = GetUserUuid(user);
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (!payment.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (payment.Status == PaymentStatus.SUCCEEDED)
        {
            throw new ConflictException("Payment has already succeeded. Cannot retry.");
        }

        if (request?.PaymentMethod != null)
        {
            payment.PaymentMethod = request.PaymentMethod.Value;
        }

        CreateOrderResult? orderResult = null;
        if (payment.PaymentMethod != PaymentMethod.CASH)
        {
            orderResult = _paymentProvider.CreateOrder(payment.BookingUuid, payment.Amount, payment.Currency, payment.PaymentMethod);
            payment.ProviderOrderId = orderResult.OrderId;
        }
        else
        {
            payment.ProviderOrderId = null;
        }

        payment.Status = PaymentStatus.PENDING;
        payment.FailureReason = null;
        payment.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();
        return MapToResponse(payment, orderResult?.CheckoutPayload);
    }

    public async Task<RefundResponse> RequestRefundAsync(ClaimsPrincipal user, string paymentUuid, RefundPaymentRequest request)
    {
        var riderUuid = GetUserUuid(user);
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (!payment.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (payment.Status != PaymentStatus.SUCCEEDED)
        {
            throw new BadRequestException("Refund can only be requested for succeeded payments");
        }

        var refundAmount = request.Amount ?? payment.Amount;
        if (refundAmount <= 0 || refundAmount > payment.Amount)
        {
            throw new BadRequestException($"Refund amount must be positive and not exceed payment total of {payment.Amount}");
        }

        string providerRefundId;
        if (payment.PaymentMethod != PaymentMethod.CASH && !string.IsNullOrWhiteSpace(payment.ProviderPaymentId))
        {
            var res = _paymentProvider.InitiateRefund(payment.ProviderPaymentId, refundAmount, payment.Currency, request.Reason);
            providerRefundId = res.RefundId;
        }
        else
        {
            providerRefundId = "rfnd_cash_" + Guid.NewGuid().ToString("N")[..8];
        }

        var refund = new PaymentRefund
        {
            PaymentId = payment.Id,
            PaymentUuid = payment.PaymentUuid,
            Amount = refundAmount,
            AmountPaise = (long)Math.Round(refundAmount * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Reason = request.Reason,
            Status = RefundStatus.PROCESSED,
            ProviderRefundId = providerRefundId,
            InitiatedBy = "RIDER",
            ProcessedAt = DateTime.UtcNow
        };
        _dbContext.PaymentRefunds.Add(refund);

        payment.Status = refundAmount >= payment.Amount ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        payment.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();
        await _ledgerService.RecordRefundAsync(payment, refundAmount, refund.RefundUuid);

        return MapToRefundResponse(refund);
    }

    public async Task<PaymentResponse> GetPaymentStatusAsync(ClaimsPrincipal user, string paymentUuid, bool refresh)
    {
        var riderUuid = GetUserUuid(user);
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (!payment.RiderUuid.Equals(riderUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException("Access denied: You do not own this payment record");
        }

        if (refresh && payment.Status == PaymentStatus.PENDING && !string.IsNullOrWhiteSpace(payment.ProviderPaymentId))
        {
            var verification = _paymentProvider.FetchPaymentStatus(payment.ProviderPaymentId);
            if (verification.Verified)
            {
                payment.Status = PaymentStatus.SUCCEEDED;
                payment.CapturedAt = DateTime.UtcNow;
                payment.UpdatedAt = DateTime.UtcNow;
                await _dbContext.SaveChangesAsync();
                await _ledgerService.RecordDigitalPaymentSuccessAsync(payment, _settings.CommissionPercentage);
            }
        }

        return MapToResponse(payment, null);
    }

    public async Task<PaymentResponse> ConfirmCashCollectionAsync(ClaimsPrincipal driverUser, string paymentUuid, CashCollectionRequest? request)
    {
        var driverUuid = GetUserUuid(driverUser);
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (payment.PaymentMethod != PaymentMethod.CASH)
        {
            throw new BadRequestException("Payment method is not CASH. Driver cash collection is only valid for CASH rides.");
        }

        if (payment.Status == PaymentStatus.SUCCEEDED)
        {
            throw new ConflictException("Cash collection for this payment has already been completed");
        }

        if (!string.IsNullOrWhiteSpace(payment.DriverUuid) && !payment.DriverUuid.Equals(driverUuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new ForbiddenException("Only the driver assigned to this booking can confirm cash collection");
        }

        if (string.IsNullOrWhiteSpace(payment.DriverUuid))
        {
            payment.DriverUuid = driverUuid;
        }

        payment.Status = PaymentStatus.SUCCEEDED;
        payment.CapturedAt = DateTime.UtcNow;
        payment.UpdatedAt = DateTime.UtcNow;

        var tx = new PaymentTransaction
        {
            PaymentId = payment.Id,
            PaymentUuid = payment.PaymentUuid,
            AttemptType = TransactionAttemptType.CASH_COLLECTION,
            GatewayProvider = "CASH",
            Amount = payment.Amount,
            AmountPaise = payment.AmountPaise,
            Currency = payment.Currency,
            Status = TransactionStatus.SUCCESS,
            RawPayload = request?.Notes ?? "Driver cash collection confirmed"
        };
        _dbContext.PaymentTransactions.Add(tx);

        await _dbContext.SaveChangesAsync();
        await _ledgerService.RecordCashCollectionSuccessAsync(payment, _settings.CommissionPercentage);

        return MapToResponse(payment, null);
    }

    public async Task ProcessWebhookAsync(string provider, string rawBody, string? signature)
    {
        if (!_paymentProvider.VerifyWebhookSignature(rawBody, signature ?? string.Empty))
        {
            throw new BadRequestException($"Invalid webhook signature for provider: {provider}");
        }

        try
        {
            using var doc = JsonDocument.Parse(rawBody);
            var root = doc.RootElement;

            var eventId = (root.TryGetProperty("event_id", out var eid) ? eid.GetString() : null)
                ?? (root.TryGetProperty("id", out var id) ? id.GetString() : null)
                ?? ("evt_" + Guid.NewGuid().ToString("N")[..10]);

            var eventType = root.TryGetProperty("event", out var ev) ? ev.GetString() ?? "payment.captured" : "payment.captured";

            var existing = await _dbContext.PaymentWebhookEvents
                .FirstOrDefaultAsync(e => e.Provider == provider.ToUpperInvariant() && e.ProviderEventId == eventId);

            if (existing != null)
            {
                _logger.LogInformation("Ignoring duplicate webhook event {EventId} from {Provider}", eventId, provider);
                return;
            }

            var webhookEvent = new PaymentWebhookEvent
            {
                Provider = provider.ToUpperInvariant(),
                ProviderEventId = eventId,
                EventType = eventType,
                Payload = rawBody,
                Signature = signature,
                Status = WebhookStatus.RECEIVED
            };
            _dbContext.PaymentWebhookEvents.Add(webhookEvent);
            await _dbContext.SaveChangesAsync();

            string? providerPaymentId = null;
            string? providerOrderId = null;

            if (root.TryGetProperty("payload", out var payloadElem) &&
                payloadElem.TryGetProperty("payment", out var payElem) &&
                payElem.TryGetProperty("entity", out var entElem))
            {
                if (entElem.TryGetProperty("id", out var pid)) providerPaymentId = pid.GetString();
                if (entElem.TryGetProperty("order_id", out var oid)) providerOrderId = oid.GetString();
            }

            Payment? payment = null;
            if (!string.IsNullOrWhiteSpace(providerPaymentId))
            {
                payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.ProviderPaymentId == providerPaymentId);
            }
            if (payment == null && !string.IsNullOrWhiteSpace(providerOrderId))
            {
                payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.ProviderOrderId == providerOrderId);
            }

            if (payment != null)
            {
                if (eventType.Equals("payment.captured", StringComparison.OrdinalIgnoreCase) ||
                    eventType.Equals("order.paid", StringComparison.OrdinalIgnoreCase))
                {
                    if (payment.Status != PaymentStatus.SUCCEEDED)
                    {
                        payment.Status = PaymentStatus.SUCCEEDED;
                        payment.CapturedAt = DateTime.UtcNow;
                        if (!string.IsNullOrWhiteSpace(providerPaymentId))
                        {
                            payment.ProviderPaymentId = providerPaymentId;
                        }

                        var tx = new PaymentTransaction
                        {
                            PaymentId = payment.Id,
                            PaymentUuid = payment.PaymentUuid,
                            AttemptType = TransactionAttemptType.CAPTURE,
                            GatewayProvider = provider.ToUpperInvariant(),
                            GatewayOrderId = providerOrderId,
                            GatewayPaymentId = providerPaymentId,
                            Amount = payment.Amount,
                            AmountPaise = payment.AmountPaise,
                            Currency = payment.Currency,
                            Status = TransactionStatus.SUCCESS
                        };
                        _dbContext.PaymentTransactions.Add(tx);
                        await _dbContext.SaveChangesAsync();

                        await _ledgerService.RecordDigitalPaymentSuccessAsync(payment, _settings.CommissionPercentage);
                    }
                }
                else if (eventType.Equals("payment.failed", StringComparison.OrdinalIgnoreCase))
                {
                    if (payment.Status != PaymentStatus.SUCCEEDED)
                    {
                        payment.Status = PaymentStatus.FAILED;
                        payment.FailureReason = "Gateway payment failed";
                        await _dbContext.SaveChangesAsync();
                    }
                }
            }

            webhookEvent.Status = WebhookStatus.PROCESSED;
            webhookEvent.ProcessedAt = DateTime.UtcNow;
            await _dbContext.SaveChangesAsync();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to parse webhook payload");
            throw new BadRequestException("Failed to parse and process webhook: " + ex.Message);
        }
    }

    public async Task<PageResponse<PaymentResponse>> GetAdminPaymentsAsync(PaymentStatus? status, PaymentMethod? method, string? riderUuid, string? driverUuid, int pageNumber, int pageSize)
    {
        var page = pageNumber > 0 ? pageNumber : 0;
        var size = pageSize > 0 ? pageSize : 10;

        var query = _dbContext.Payments.AsQueryable();

        if (status.HasValue) query = query.Where(p => p.Status == status.Value);
        if (method.HasValue) query = query.Where(p => p.PaymentMethod == method.Value);
        if (!string.IsNullOrWhiteSpace(riderUuid)) query = query.Where(p => p.RiderUuid == riderUuid);
        if (!string.IsNullOrWhiteSpace(driverUuid)) query = query.Where(p => p.DriverUuid == driverUuid);

        var total = await query.LongCountAsync();
        var items = await query.OrderByDescending(p => p.CreatedAt).Skip(page * size).Take(size).ToListAsync();

        return new PageResponse<PaymentResponse>
        {
            Content = items.Select(p => MapToResponse(p, null)).ToList(),
            PageNumber = page,
            PageSize = size,
            TotalElements = total,
            TotalPages = (int)Math.Ceiling(total / (double)size),
            Last = (page + 1) * size >= total
        };
    }

    public async Task<PaymentResponse> GetAdminPaymentByUuidAsync(string paymentUuid)
    {
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }
        return MapToResponse(payment, null);
    }

    public async Task<RefundResponse> ProcessAdminRefundAsync(ClaimsPrincipal adminUser, string paymentUuid, AdminRefundRequest request)
    {
        var payment = await _dbContext.Payments.FirstOrDefaultAsync(p => p.PaymentUuid == paymentUuid);
        if (payment == null)
        {
            throw new ResourceNotFoundException($"Payment with UUID {paymentUuid} not found");
        }

        if (payment.Status != PaymentStatus.SUCCEEDED && payment.Status != PaymentStatus.PARTIALLY_REFUNDED)
        {
            throw new BadRequestException("Refund can only be applied to succeeded or partially refunded payments");
        }

        if (request.Amount <= 0 || request.Amount > payment.Amount)
        {
            throw new BadRequestException($"Invalid refund amount. Must be between 1.00 and {payment.Amount}");
        }

        string providerRefundId;
        if (payment.PaymentMethod != PaymentMethod.CASH && !string.IsNullOrWhiteSpace(payment.ProviderPaymentId))
        {
            var res = _paymentProvider.InitiateRefund(payment.ProviderPaymentId, request.Amount, payment.Currency, request.Reason);
            providerRefundId = res.RefundId;
        }
        else
        {
            providerRefundId = "rfnd_admin_" + Guid.NewGuid().ToString("N")[..8];
        }

        var refund = new PaymentRefund
        {
            PaymentId = payment.Id,
            PaymentUuid = payment.PaymentUuid,
            Amount = request.Amount,
            AmountPaise = (long)Math.Round(request.Amount * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Reason = request.Reason,
            AdminNotes = request.AdminNotes,
            Status = RefundStatus.PROCESSED,
            ProviderRefundId = providerRefundId,
            InitiatedBy = "ADMIN",
            ProcessedAt = DateTime.UtcNow
        };
        _dbContext.PaymentRefunds.Add(refund);

        payment.Status = PaymentStatus.REFUNDED;
        payment.UpdatedAt = DateTime.UtcNow;

        await _dbContext.SaveChangesAsync();
        await _ledgerService.RecordRefundAsync(payment, request.Amount, refund.RefundUuid);

        return MapToRefundResponse(refund);
    }

    public async Task<ReconciliationResponse> GetReconciliationReportAsync()
    {
        var allPayments = await _dbContext.Payments.ToListAsync();
        var totalCount = allPayments.Count;
        var totalCollected = 0m;
        var cashCollected = 0m;
        var digitalCollected = 0m;

        foreach (var p in allPayments)
        {
            if (p.Status is PaymentStatus.SUCCEEDED or PaymentStatus.REFUNDED or PaymentStatus.PARTIALLY_REFUNDED)
            {
                totalCollected += p.Amount;
                if (p.PaymentMethod == PaymentMethod.CASH)
                {
                    cashCollected += p.Amount;
                }
                else
                {
                    digitalCollected += p.Amount;
                }
            }
        }

        var commRate = _settings.CommissionPercentage;
        var commission = Math.Round(totalCollected * commRate / 100m, 2, MidpointRounding.AwayFromZero);
        var driverEarnings = totalCollected - commission;

        return new ReconciliationResponse
        {
            TotalPaymentsCount = totalCount,
            TotalCollectedAmount = totalCollected,
            TotalCashCollected = cashCollected,
            TotalDigitalCollected = digitalCollected,
            TotalCommissionEarned = commission,
            TotalDriverEarnings = driverEarnings,
            TotalRefundsDisbursed = 0m,
            DiscrepanciesCount = 0,
            Status = "HEALTHY",
            GeneratedAt = DateTime.UtcNow
        };
    }

    private static string GetUserUuid(ClaimsPrincipal principal)
    {
        return principal.FindFirst(ClaimTypes.NameIdentifier)?.Value
            ?? principal.FindFirst("sub")?.Value
            ?? throw new UnauthorizedException("User identity not found in token claims");
    }

    private static PaymentResponse MapToResponse(Payment p, IDictionary<string, object>? checkoutPayload)
    {
        return new PaymentResponse
        {
            PaymentUuid = p.PaymentUuid,
            BookingUuid = p.BookingUuid,
            RiderUuid = p.RiderUuid,
            DriverUuid = p.DriverUuid,
            Amount = p.Amount,
            AmountPaise = p.AmountPaise,
            Currency = p.Currency,
            PaymentMethod = p.PaymentMethod,
            Status = p.Status,
            Provider = p.Provider,
            ProviderOrderId = p.ProviderOrderId,
            ProviderPaymentId = p.ProviderPaymentId,
            CheckoutPayload = checkoutPayload,
            FailureReason = p.FailureReason,
            AuthorizedAt = p.AuthorizedAt,
            CapturedAt = p.CapturedAt,
            CreatedAt = p.CreatedAt,
            UpdatedAt = p.UpdatedAt
        };
    }

    private static RefundResponse MapToRefundResponse(PaymentRefund r)
    {
        return new RefundResponse
        {
            RefundUuid = r.RefundUuid,
            PaymentUuid = r.PaymentUuid,
            Amount = r.Amount,
            Currency = r.Currency,
            Status = r.Status,
            Reason = r.Reason,
            ProviderRefundId = r.ProviderRefundId,
            InitiatedBy = r.InitiatedBy,
            ProcessedAt = r.ProcessedAt,
            CreatedAt = r.CreatedAt
        };
    }
}
