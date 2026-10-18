package com.lexor.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.booking.entity.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Comprehensive booking details and journey lifecycle response")
public class BookingResponse {

    @Schema(description = "Booking public UUID identifier", example = "55555555-5555-4555-8555-555555555555")
    private String bookingUuid;

    @Schema(description = "Referenced ride request public UUID", example = "44444444-4444-4444-8444-444444444444")
    private String rideRequestUuid;

    @Schema(description = "Current lifecycle status of the booking", example = "CONFIRMED")
    private BookingStatus status;

    @Schema(description = "Authenticated rider summary")
    private RiderSummaryResponse rider;

    @Schema(description = "Assigned driver summary")
    private DriverSummaryResponse driver;

    @Schema(description = "Assigned vehicle summary")
    private VehicleSummaryResponse vehicle;

    @Schema(description = "Selected ride category summary")
    private RideTypeSummaryResponse rideType;

    @Schema(description = "Pickup location street address", example = "MG Road, Indiranagar, Bengaluru")
    private String pickupAddress;

    @Schema(description = "Pickup latitude coordinate", example = "12.97840000")
    private BigDecimal pickupLatitude;

    @Schema(description = "Pickup longitude coordinate", example = "77.64080000")
    private BigDecimal pickupLongitude;

    @Schema(description = "Destination drop-off street address", example = "Prestige Tech Park, Marathahalli, Bengaluru")
    private String dropAddress;

    @Schema(description = "Destination drop latitude coordinate", example = "12.93720000")
    private BigDecimal dropLatitude;

    @Schema(description = "Destination drop longitude coordinate", example = "77.69490000")
    private BigDecimal dropLongitude;

    @Schema(description = "Estimated journey distance in kilometers", example = "11.50")
    private BigDecimal estimatedDistanceKm;

    @Schema(description = "Estimated journey duration in minutes", example = "32")
    private Integer estimatedDurationMinutes;

    @Schema(description = "Total estimated or finalized fare", example = "250.00")
    private BigDecimal totalFare;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Plain 4-digit OTP provided to rider at booking creation", example = "4829")
    private String otp;

    @Schema(description = "Whether rider OTP has been verified by driver at trip start", example = "true")
    private Boolean otpVerified;

    @Schema(description = "Scheduled trip pickup time", example = "2026-10-19T10:00:00")
    private LocalDateTime scheduledTime;

    @Schema(description = "Cancellation reason description", example = "Change of plans")
    private String cancellationReason;

    @Schema(description = "Entity that cancelled the booking (RIDER, DRIVER, ADMIN)")
    private String cancelledBy;

    @Schema(description = "Timestamp when driver accepted the offered booking")
    private LocalDateTime acceptedAt;

    @Schema(description = "Timestamp when driver arrived at pickup location")
    private LocalDateTime arrivedAt;

    @Schema(description = "Timestamp when trip was officially started")
    private LocalDateTime startedAt;

    @Schema(description = "Timestamp when trip was successfully completed")
    private LocalDateTime completedAt;

    @Schema(description = "Timestamp when booking was cancelled")
    private LocalDateTime cancelledAt;

    @Schema(description = "Booking creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Booking last update timestamp")
    private LocalDateTime updatedAt;
}
