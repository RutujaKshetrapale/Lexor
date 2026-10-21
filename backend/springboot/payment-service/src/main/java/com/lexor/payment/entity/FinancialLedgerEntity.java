package com.lexor.payment.entity;

import com.lexor.payment.enums.LedgerAccount;
import com.lexor.payment.enums.LedgerDirection;
import com.lexor.payment.enums.LedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "financial_ledger_entries")
public class FinancialLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_uuid", nullable = false, unique = true, length = 36)
    private String entryUuid;

    @Column(name = "payment_uuid", nullable = false, length = 36)
    private String paymentUuid;

    @Column(name = "booking_uuid", nullable = false, length = 36)
    private String bookingUuid;

    @Column(name = "driver_uuid", length = 36)
    private String driverUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private LedgerDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "account", nullable = false, length = 40)
    private LedgerAccount account;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (entryUuid == null || entryUuid.isBlank()) {
            entryUuid = UUID.randomUUID().toString();
        }
        if (currency == null) {
            currency = "INR";
        }
        if (amountPaise == null && amount != null) {
            amountPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
        }
        createdAt = LocalDateTime.now();
    }
}
