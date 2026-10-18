package com.lexor.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Payload to request cancellation of a booking")
public class CancelBookingRequest {

    @Size(max = 255, message = "Cancellation reason cannot exceed 255 characters")
    @Schema(description = "Reason description for cancellation", example = "Change of schedule")
    private String reason;
}
