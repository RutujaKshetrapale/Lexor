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
@Schema(description = "Rider profile summary")
public class RiderSummaryResponse {

    @Schema(description = "Rider public UUID identifier", example = "22222222-2222-4222-8222-222222222222")
    private String riderUuid;

    @Schema(description = "First name", example = "Rajesh")
    private String firstName;

    @Schema(description = "Last name", example = "Kumar")
    private String lastName;

    @Schema(description = "Email", example = "rajesh.kumar@example.com")
    private String email;

    @Schema(description = "Phone number", example = "+919876543211")
    private String phoneNumber;

    @Schema(description = "Rider rating", example = "4.95")
    private BigDecimal rating;
}
