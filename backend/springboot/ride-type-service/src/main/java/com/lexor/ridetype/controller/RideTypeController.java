package com.lexor.ridetype.controller;

import com.lexor.ridetype.dto.response.ErrorResponse;
import com.lexor.ridetype.dto.response.RideTypeResponse;
import com.lexor.ridetype.service.RideTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ride-types")
@RequiredArgsConstructor
@Tag(name = "Ride Type Catalogue", description = "Rider and driver facing endpoints to view active ride categories and pricing details")
@SecurityRequirement(name = "BearerAuthentication")
public class RideTypeController {

    private final RideTypeService rideTypeService;

    @Operation(
            summary = "List available ride types",
            description = "Retrieves all currently enabled and active ride categories available for booking in the platform catalogue."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Enabled ride types retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RideTypeResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<RideTypeResponse>> getAvailableRideTypes() {
        List<RideTypeResponse> rideTypes = rideTypeService.getEnabledRideTypes();
        return ResponseEntity.ok(rideTypes);
    }

    @Operation(
            summary = "Get ride type details by UUID",
            description = "Retrieves details of a single enabled ride type by its public UUID identifier. Returns 404 if disabled or non-existent."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride type retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RideTypeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride type not found or inactive",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{rideTypeUuid}")
    public ResponseEntity<RideTypeResponse> getRideTypeByUuid(
            @Parameter(description = "Public UUID of the ride type", required = true)
            @PathVariable String rideTypeUuid
    ) {
        RideTypeResponse rideType = rideTypeService.getEnabledRideTypeByUuid(rideTypeUuid);
        return ResponseEntity.ok(rideType);
    }
}
