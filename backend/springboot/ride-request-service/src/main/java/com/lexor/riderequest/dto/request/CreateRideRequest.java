package com.lexor.riderequest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to submit a new ride request")
public class CreateRideRequest {

    @NotBlank(message = "Pickup address is required")
    @Size(min = 3, max = 255, message = "Pickup address must be between 3 and 255 characters")
    @Schema(description = "Detailed pickup location or landmark description", example = "MG Road, Indiranagar, Bengaluru")
    private String pickupAddress;

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0", message = "Pickup latitude must be between -90.0 and 90.0")
    @DecimalMax(value = "90.0", message = "Pickup latitude must be between -90.0 and 90.0")
    @Schema(description = "Pickup latitude coordinate", example = "12.97840000")
    private BigDecimal pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0", message = "Pickup longitude must be between -180.0 and 180.0")
    @DecimalMax(value = "180.0", message = "Pickup longitude must be between -180.0 and 180.0")
    @Schema(description = "Pickup longitude coordinate", example = "77.64080000")
    private BigDecimal pickupLongitude;

    @NotBlank(message = "Drop address is required")
    @Size(min = 3, max = 255, message = "Drop address must be between 3 and 255 characters")
    @Schema(description = "Detailed destination location or landmark description", example = "Prestige Tech Park, Marathahalli, Bengaluru")
    private String dropAddress;

    @NotNull(message = "Drop latitude is required")
    @DecimalMin(value = "-90.0", message = "Drop latitude must be between -90.0 and 90.0")
    @DecimalMax(value = "90.0", message = "Drop latitude must be between -90.0 and 90.0")
    @Schema(description = "Drop latitude coordinate", example = "12.93720000")
    private BigDecimal dropLatitude;

    @NotNull(message = "Drop longitude is required")
    @DecimalMin(value = "-180.0", message = "Drop longitude must be between -180.0 and 180.0")
    @DecimalMax(value = "180.0", message = "Drop longitude must be between -180.0 and 180.0")
    @Schema(description = "Drop longitude coordinate", example = "77.69490000")
    private BigDecimal dropLongitude;

    @Schema(description = "Public UUID identifier of the chosen ride category", example = "a1b2c3d4-0001-4000-8000-000000000001")
    private String rideTypeUuid;

    @Schema(description = "Unique category code identifier of the chosen ride category", example = "GO")
    private String rideTypeCode;

    @Schema(description = "Optional coupon ID for promotional discounts", example = "1")
    private Long couponId;
}
