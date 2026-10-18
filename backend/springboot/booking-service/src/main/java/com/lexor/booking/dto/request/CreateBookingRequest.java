package com.lexor.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to create a booking from a valid ride request")
public class CreateBookingRequest {

    @NotBlank(message = "Ride request UUID is mandatory")
    @Schema(description = "Public UUID identifier of the eligible ride request", example = "44444444-4444-4444-8444-444444444444")
    private String rideRequestUuid;

    @Schema(description = "Optional scheduled pickup time for advance bookings", example = "2026-10-19T10:00:00")
    private LocalDateTime scheduledTime;

    @Schema(description = "Client-supplied idempotency key to prevent duplicate booking creation", example = "idemp-bk-9872134")
    private String idempotencyKey;
}
