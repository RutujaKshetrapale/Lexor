package com.lexor.payment.service.impl;

import com.lexor.payment.entity.FinancialLedgerEntity;
import com.lexor.payment.entity.PaymentEntity;
import com.lexor.payment.enums.LedgerAccount;
import com.lexor.payment.enums.LedgerDirection;
import com.lexor.payment.enums.LedgerEntryType;
import com.lexor.payment.repository.FinancialLedgerRepository;
import com.lexor.payment.service.ILedgerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements ILedgerService {

    private static final Logger logger = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final FinancialLedgerRepository ledgerRepository;

    @Override
    @Transactional
    public void recordDigitalPaymentSuccess(PaymentEntity payment, BigDecimal platformCommissionPercentage) {
        BigDecimal totalFare = payment.getAmount();
        BigDecimal commissionRate = platformCommissionPercentage != null ? platformCommissionPercentage : BigDecimal.valueOf(20.00);

        BigDecimal platformCommission = totalFare
                .multiply(commissionRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal driverEarnings = totalFare.subtract(platformCommission);

        // 1. Rider Collection Credit -> Gateway Clearing
        FinancialLedgerEntity fareEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.FARE_COLLECTED)
                .direction(LedgerDirection.CREDIT)
                .account(LedgerAccount.GATEWAY_CLEARING)
                .amount(totalFare)
                .amountPaise(totalFare.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Digital ride fare collected via " + payment.getPaymentMethod())
                .referenceId(payment.getProviderPaymentId())
                .build();
        ledgerRepository.save(fareEntry);

        // 2. Platform Commission Credit -> Platform Revenue
        FinancialLedgerEntity commissionEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.PLATFORM_COMMISSION)
                .direction(LedgerDirection.CREDIT)
                .account(LedgerAccount.PLATFORM_REVENUE)
                .amount(platformCommission)
                .amountPaise(platformCommission.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Platform commission (" + commissionRate + "%) on digital collection")
                .referenceId(payment.getPaymentUuid())
                .build();
        ledgerRepository.save(commissionEntry);

        // 3. Driver Earnings Credit -> Driver Payable
        FinancialLedgerEntity driverEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.DRIVER_PAYABLE)
                .direction(LedgerDirection.CREDIT)
                .account(LedgerAccount.DRIVER_PAYABLE)
                .amount(driverEarnings)
                .amountPaise(driverEarnings.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Driver net payout payable (" + BigDecimal.valueOf(100).subtract(commissionRate) + "%)")
                .referenceId(payment.getPaymentUuid())
                .build();
        ledgerRepository.save(driverEntry);

        logger.info("Recorded digital ledger entries for payment {}: total={}, commission={}, driverPayable={}",
                payment.getPaymentUuid(), totalFare, platformCommission, driverEarnings);
    }

    @Override
    @Transactional
    public void recordCashCollectionSuccess(PaymentEntity payment, BigDecimal platformCommissionPercentage) {
        BigDecimal totalFare = payment.getAmount();
        BigDecimal commissionRate = platformCommissionPercentage != null ? platformCommissionPercentage : BigDecimal.valueOf(20.00);

        BigDecimal platformCommission = totalFare
                .multiply(commissionRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal driverRetainedEarnings = totalFare.subtract(platformCommission);

        // 1. Cash Collected By Driver -> Cash Clearing
        FinancialLedgerEntity cashCollectedEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.CASH_COLLECTED_BY_DRIVER)
                .direction(LedgerDirection.DEBIT)
                .account(LedgerAccount.CASH_CLEARING)
                .amount(totalFare)
                .amountPaise(totalFare.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Cash fare collected directly by driver")
                .referenceId("CASH-" + payment.getBookingUuid())
                .build();
        ledgerRepository.save(cashCollectedEntry);

        // 2. Driver Cash Liability (Driver holds 100% of cash, owes 20% platform commission)
        FinancialLedgerEntity liabilityEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.DRIVER_CASH_LIABILITY)
                .direction(LedgerDirection.DEBIT)
                .account(LedgerAccount.PLATFORM_REVENUE)
                .amount(platformCommission)
                .amountPaise(platformCommission.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Driver liability to platform for cash ride commission (" + commissionRate + "%)")
                .referenceId(payment.getPaymentUuid())
                .build();
        ledgerRepository.save(liabilityEntry);

        // 3. Driver Earnings directly retained in cash
        FinancialLedgerEntity driverRetainedEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.DRIVER_EARNINGS)
                .direction(LedgerDirection.CREDIT)
                .account(LedgerAccount.DRIVER_PAYABLE)
                .amount(driverRetainedEarnings)
                .amountPaise(driverRetainedEarnings.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Driver net earnings retained directly from collected cash")
                .referenceId(payment.getPaymentUuid())
                .build();
        ledgerRepository.save(driverRetainedEntry);

        logger.info("Recorded cash collection ledger entries for payment {}: total={}, commissionLiability={}, driverRetained={}",
                payment.getPaymentUuid(), totalFare, platformCommission, driverRetainedEarnings);
    }

    @Override
    @Transactional
    public void recordRefund(PaymentEntity payment, BigDecimal refundAmount, String refundUuid) {
        FinancialLedgerEntity refundEntry = FinancialLedgerEntity.builder()
                .entryUuid(UUID.randomUUID().toString())
                .paymentUuid(payment.getPaymentUuid())
                .bookingUuid(payment.getBookingUuid())
                .driverUuid(payment.getDriverUuid())
                .entryType(LedgerEntryType.REFUND_DEDUCTION)
                .direction(LedgerDirection.DEBIT)
                .account(LedgerAccount.REFUND_RESERVE)
                .amount(refundAmount)
                .amountPaise(refundAmount.multiply(BigDecimal.valueOf(100)).longValue())
                .currency(payment.getCurrency())
                .description("Refund processed for payment " + payment.getPaymentUuid())
                .referenceId(refundUuid)
                .build();
        ledgerRepository.save(refundEntry);

        logger.info("Recorded refund ledger entry for payment {}: refundAmount={}", payment.getPaymentUuid(), refundAmount);
    }
}
