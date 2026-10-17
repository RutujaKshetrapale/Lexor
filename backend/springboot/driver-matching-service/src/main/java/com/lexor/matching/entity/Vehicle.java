package com.lexor.matching.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverProfile driver;

    @Column(name = "make", nullable = false, length = 50)
    private String make;

    @Column(name = "model", nullable = false, length = 50)
    private String model;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "color", nullable = false, length = 30)
    private String color;

    @Column(name = "license_plate", nullable = false, unique = true, length = 20)
    private String licensePlate;

    @Column(name = "vin", unique = true, length = 17)
    private String vin;

    @Column(name = "ride_type_category", nullable = false, length = 30)
    private String rideTypeCategory;

    @Column(name = "seat_capacity", nullable = false)
    @Builder.Default
    private Integer seatCapacity = 4;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false)
    @Builder.Default
    private FuelType fuelType = FuelType.PETROL;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_inspected", nullable = false)
    @Builder.Default
    private boolean isInspected = true;

    @Column(name = "inspection_expires_at")
    private LocalDate inspectionExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @Builder.Default
    private VehicleVerificationStatus verificationStatus = VehicleVerificationStatus.PENDING_VERIFICATION;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEligibleForMatching(String requestedCategoryCode) {
        if (!isActive || verificationStatus != VehicleVerificationStatus.VERIFIED || !isInspected) {
            return false;
        }
        if (inspectionExpiresAt != null && inspectionExpiresAt.isBefore(LocalDate.now())) {
            return false;
        }
        if (requestedCategoryCode == null) {
            return true;
        }
        return rideTypeCategory.equalsIgnoreCase(requestedCategoryCode);
    }
}
