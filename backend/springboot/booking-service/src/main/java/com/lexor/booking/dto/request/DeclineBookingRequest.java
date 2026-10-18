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
@Schema(description = "Payload for driver to decline an offered booking")
public class DeclineBookingRequest {

    @Size(max = 255, message = "Decline reason cannot exceed 255 characters")
    @Schema(description = "Reason description for declining the booking", example = "Pickup location is outside preferred zone")
    private String reason;
}
