package com.lexor.payment.entity;

import com.lexor.payment.enums.PayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "driver_payout_records")
public class DriverPayoutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payout_uuid", nullable = false, unique = true, length = 36)
    private String payoutUuid;

    @Column(name = "driver_uuid", nullable = false, length = 36)
    private String driverUuid;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "gross_earnings", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal grossEarnings = BigDecimal.ZERO;

    @Column(name = "platform_commission", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal platformCommission = BigDecimal.ZERO;

    @Column(name = "cash_collected", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cashCollected = BigDecimal.ZERO;

    @Column(name = "net_payout_due", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal netPayoutDue = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.CALCULATED;

    @Column(name = "provider_payout_reference", length = 100)
    private String providerPayoutReference;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (payoutUuid == null || payoutUuid.isBlank()) {
            payoutUuid = UUID.randomUUID().toString();
        }
        if (currency == null) {
            currency = "INR";
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
