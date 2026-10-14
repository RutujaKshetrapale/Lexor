package com.lexor.ridetype.controller;

import com.lexor.ridetype.dto.request.CreateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeStatusRequest;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/ride-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Ride Type Management", description = "Endpoints for administrators to create, update, configure fares, enable/disable, and monitor ride types")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminRideTypeController {

    private final RideTypeService rideTypeService;

    @Operation(
            summary = "Create a new ride type",
            description = "Creates a new ride type category with fare pricing rules. Enforces unique codes and names."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Ride type created successfully",
                    content = @Content(schema = @Schema(implementation = RideTypeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Validation failure or invalid fares",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Duplicate code or name",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<RideTypeResponse> createRideType(@Valid @RequestBody CreateRideTypeRequest request) {
        RideTypeResponse response = rideTypeService.createRideType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List all ride types (ADMIN)",
            description = "Retrieves all ride types in the system, including disabled and inactive categories."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "All ride types retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RideTypeResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<RideTypeResponse>> getAllRideTypes() {
        List<RideTypeResponse> rideTypes = rideTypeService.getAllRideTypesAdmin();
        return ResponseEntity.ok(rideTypes);
    }

    @Operation(
            summary = "Get ride type details by UUID (ADMIN)",
            description = "Retrieves full details of a specific ride type by UUID, including inactive status and timestamps."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride type details retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RideTypeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride type not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{rideTypeUuid}")
    public ResponseEntity<RideTypeResponse> getRideTypeByUuid(
            @Parameter(description = "Public UUID of the ride type", required = true)
            @PathVariable String rideTypeUuid
    ) {
        RideTypeResponse response = rideTypeService.getRideTypeByUuidAdmin(rideTypeUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Update permitted ride type fields",
            description = "Updates display name, description, fare rates, passenger capacity, and icon URL for a ride type."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride type updated successfully",
                    content = @Content(schema = @Schema(implementation = RideTypeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Validation error or invalid fare constraints",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride type not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Updated name or code duplicates existing category",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PutMapping("/{rideTypeUuid}")
    public ResponseEntity<RideTypeResponse> updateRideType(
            @Parameter(description = "Public UUID of the ride type", required = true)
            @PathVariable String rideTypeUuid,
            @Valid @RequestBody UpdateRideTypeRequest request
    ) {
        RideTypeResponse response = rideTypeService.updateRideType(rideTypeUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Enable or disable a ride type",
            description = "Toggles the active status of a ride type. Disabling hides the category from rider catalogue while preserving historical integrity."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride type status updated successfully",
                    content = @Content(schema = @Schema(implementation = RideTypeResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Cannot disable the last active ride category",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride type not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{rideTypeUuid}/status")
    public ResponseEntity<RideTypeResponse> updateRideTypeStatus(
            @Parameter(description = "Public UUID of the ride type", required = true)
            @PathVariable String rideTypeUuid,
            @Valid @RequestBody UpdateRideTypeStatusRequest request
    ) {
        RideTypeResponse response = rideTypeService.updateRideTypeStatus(rideTypeUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Safely delete a ride type",
            description = "Attempts deletion of a ride type. Rejects hard deletion if historical booking/ride records exist, preserving platform referential integrity."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request - Hard deletion forbidden to protect historical business records",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride type not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @DeleteMapping("/{rideTypeUuid}")
    public ResponseEntity<Void> deleteRideType(
            @Parameter(description = "Public UUID of the ride type", required = true)
            @PathVariable String rideTypeUuid
    ) {
        rideTypeService.deleteRideTypeSafely(rideTypeUuid);
        return ResponseEntity.noContent().build();
    }
}
