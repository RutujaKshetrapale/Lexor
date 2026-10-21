using PaymentService.Data;
using PaymentService.Entities;
using PaymentService.Enums;

namespace PaymentService.Services;

public interface ILedgerService
{
    Task RecordDigitalPaymentSuccessAsync(Payment payment, decimal platformCommissionPercentage);
    Task RecordCashCollectionSuccessAsync(Payment payment, decimal platformCommissionPercentage);
    Task RecordRefundAsync(Payment payment, decimal refundAmount, string refundUuid);
}

public class LedgerService : ILedgerService
{
    private readonly PaymentDbContext _dbContext;
    private readonly ILogger<LedgerService> _logger;

    public LedgerService(PaymentDbContext dbContext, ILogger<LedgerService> logger)
    {
        _dbContext = dbContext;
        _logger = logger;
    }

    public async Task RecordDigitalPaymentSuccessAsync(Payment payment, decimal platformCommissionPercentage)
    {
        var totalFare = payment.Amount;
        var commissionRate = platformCommissionPercentage > 0 ? platformCommissionPercentage : 20.00m;

        var platformCommission = Math.Round(totalFare * commissionRate / 100m, 2, MidpointRounding.AwayFromZero);
        var driverEarnings = totalFare - platformCommission;

        // 1. Fare Collected -> Gateway Clearing
        var fareEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.FARE_COLLECTED,
            Direction = LedgerDirection.CREDIT,
            Account = LedgerAccount.GATEWAY_CLEARING,
            Amount = totalFare,
            AmountPaise = (long)Math.Round(totalFare * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = $"Digital ride fare collected via {payment.PaymentMethod}",
            ReferenceId = payment.ProviderPaymentId
        };
        _dbContext.FinancialLedgerEntries.Add(fareEntry);

        // 2. Platform Commission -> Platform Revenue
        var commissionEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.PLATFORM_COMMISSION,
            Direction = LedgerDirection.CREDIT,
            Account = LedgerAccount.PLATFORM_REVENUE,
            Amount = platformCommission,
            AmountPaise = (long)Math.Round(platformCommission * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = $"Platform commission ({commissionRate}%) on digital collection",
            ReferenceId = payment.PaymentUuid
        };
        _dbContext.FinancialLedgerEntries.Add(commissionEntry);

        // 3. Driver Payable -> Driver Payable
        var driverEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.DRIVER_PAYABLE,
            Direction = LedgerDirection.CREDIT,
            Account = LedgerAccount.DRIVER_PAYABLE,
            Amount = driverEarnings,
            AmountPaise = (long)Math.Round(driverEarnings * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = $"Driver net payout payable ({100m - commissionRate}%)",
            ReferenceId = payment.PaymentUuid
        };
        _dbContext.FinancialLedgerEntries.Add(driverEntry);

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Recorded digital ledger entries for payment {PaymentUuid}: total={Total}, commission={Commission}, driverPayable={DriverPayable}",
            payment.PaymentUuid, totalFare, platformCommission, driverEarnings);
    }

    public async Task RecordCashCollectionSuccessAsync(Payment payment, decimal platformCommissionPercentage)
    {
        var totalFare = payment.Amount;
        var commissionRate = platformCommissionPercentage > 0 ? platformCommissionPercentage : 20.00m;

        var platformCommission = Math.Round(totalFare * commissionRate / 100m, 2, MidpointRounding.AwayFromZero);
        var driverRetainedEarnings = totalFare - platformCommission;

        // 1. Cash Collected By Driver -> Cash Clearing
        var cashEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.CASH_COLLECTED_BY_DRIVER,
            Direction = LedgerDirection.DEBIT,
            Account = LedgerAccount.CASH_CLEARING,
            Amount = totalFare,
            AmountPaise = (long)Math.Round(totalFare * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = "Cash fare collected directly by driver",
            ReferenceId = $"CASH-{payment.BookingUuid}"
        };
        _dbContext.FinancialLedgerEntries.Add(cashEntry);

        // 2. Driver Cash Liability -> Platform Revenue
        var liabilityEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.DRIVER_CASH_LIABILITY,
            Direction = LedgerDirection.DEBIT,
            Account = LedgerAccount.PLATFORM_REVENUE,
            Amount = platformCommission,
            AmountPaise = (long)Math.Round(platformCommission * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = $"Driver liability to platform for cash ride commission ({commissionRate}%)",
            ReferenceId = payment.PaymentUuid
        };
        _dbContext.FinancialLedgerEntries.Add(liabilityEntry);

        // 3. Driver Earnings directly retained in cash
        var driverRetainedEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.DRIVER_EARNINGS,
            Direction = LedgerDirection.CREDIT,
            Account = LedgerAccount.DRIVER_PAYABLE,
            Amount = driverRetainedEarnings,
            AmountPaise = (long)Math.Round(driverRetainedEarnings * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = "Driver net earnings retained directly from collected cash",
            ReferenceId = payment.PaymentUuid
        };
        _dbContext.FinancialLedgerEntries.Add(driverRetainedEntry);

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Recorded cash collection ledger entries for payment {PaymentUuid}: total={Total}, commissionLiability={Commission}, driverRetained={Retained}",
            payment.PaymentUuid, totalFare, platformCommission, driverRetainedEarnings);
    }

    public async Task RecordRefundAsync(Payment payment, decimal refundAmount, string refundUuid)
    {
        var refundEntry = new FinancialLedgerEntry
        {
            PaymentUuid = payment.PaymentUuid,
            BookingUuid = payment.BookingUuid,
            DriverUuid = payment.DriverUuid,
            EntryType = LedgerEntryType.REFUND_DEDUCTION,
            Direction = LedgerDirection.DEBIT,
            Account = LedgerAccount.REFUND_RESERVE,
            Amount = refundAmount,
            AmountPaise = (long)Math.Round(refundAmount * 100, MidpointRounding.AwayFromZero),
            Currency = payment.Currency,
            Description = $"Refund processed for payment {payment.PaymentUuid}",
            ReferenceId = refundUuid
        };
        _dbContext.FinancialLedgerEntries.Add(refundEntry);

        await _dbContext.SaveChangesAsync();
        _logger.LogInformation("Recorded refund ledger entry for payment {PaymentUuid}: refundAmount={RefundAmount}",
            payment.PaymentUuid, refundAmount);
    }
}
