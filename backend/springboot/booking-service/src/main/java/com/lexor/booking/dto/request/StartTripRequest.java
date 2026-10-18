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
@Schema(description = "Payload to start the trip, including optional rider OTP verification")
public class StartTripRequest {

    @Size(min = 4, max = 6, message = "OTP must be between 4 and 6 characters")
    @Schema(description = "One-Time Password provided by rider at pickup", example = "4829")
    private String otp;
}
