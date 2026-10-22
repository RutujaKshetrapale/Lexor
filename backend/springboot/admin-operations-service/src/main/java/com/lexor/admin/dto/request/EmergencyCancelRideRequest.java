package com.lexor.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyCancelRideRequest {

    @NotBlank(message = "Booking UUID is required")
    private String bookingUuid;

    @NotBlank(message = "Mandatory cancellation reason is required for administrative audit")
    private String reason;
}
