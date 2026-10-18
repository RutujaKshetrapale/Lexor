package com.lexor.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Driver profile summary")
public class DriverSummaryResponse {

    @Schema(description = "Driver public UUID identifier", example = "33333333-3333-4333-8333-333333333333")
    private String driverUuid;

    @Schema(description = "First name", example = "Vikram")
    private String firstName;

    @Schema(description = "Last name", example = "Singh")
    private String lastName;

    @Schema(description = "Phone number", example = "+919876543212")
    private String phoneNumber;

    @Schema(description = "Driver rating", example = "4.90")
    private BigDecimal rating;

    @Schema(description = "Total trips completed", example = "145")
    private Integer totalTrips;
}
