package com.lexor.riderequest.dto.request;

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
@Schema(description = "Payload to cancel a ride request")
public class CancelRideRequest {

    @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters")
    @Schema(description = "Reason for cancelling the ride request", example = "Changed my mind / plan changed")
    private String reason;
}
