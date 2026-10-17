package com.lexor.matching.controller;

import com.lexor.matching.dto.request.LocationUpdateRequest;
import com.lexor.matching.dto.response.ErrorResponse;
import com.lexor.matching.dto.response.LocationUpdateResponse;
import com.lexor.matching.security.UserPrincipal;
import com.lexor.matching.service.DriverLocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
@Tag(name = "Driver Telemetry", description = "Endpoints for authenticated drivers to broadcast current location telemetry")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('DRIVER')")
public class DriverLocationController {

    private final DriverLocationService driverLocationService;

    @Operation(
            summary = "Submit driver location update",
            description = "Ingests validated GPS coordinates, heading, and speed telemetry for the authenticated driver. Enforces driver ownership from security context."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Location telemetry accepted and recorded successfully",
                    content = @Content(schema = @Schema(implementation = LocationUpdateResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Validation failure (invalid coordinates or future timestamp)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/location")
    public ResponseEntity<LocationUpdateResponse> updateLocation(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        LocationUpdateResponse response = driverLocationService.updateDriverLocation(driverPrincipal, request);
        return ResponseEntity.ok(response);
    }
}
