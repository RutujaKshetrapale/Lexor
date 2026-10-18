package com.lexor.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response payload")
public class ErrorResponse {

    @Schema(description = "Error timestamp", example = "2026-10-18T12:00:00")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error code identifier", example = "BAD_REQUEST")
    private String error;

    @Schema(description = "Human-readable error description", example = "Invalid booking transition")
    private String message;

    @Schema(description = "Requested resource path", example = "/api/v1/bookings")
    private String path;

    @Schema(description = "Field-level validation error details")
    private Map<String, String> details;
}
