package com.lexor.vehicle.controller;

import com.lexor.vehicle.dto.request.RegisterVehicleRequest;
import com.lexor.vehicle.dto.request.UpdateVehicleRequest;
import com.lexor.vehicle.dto.request.VehicleStatusUpdateRequest;
import com.lexor.vehicle.dto.response.DriverVehicleAssignmentResponse;
import com.lexor.vehicle.dto.response.ErrorResponse;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationHistoryResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationStatusResponse;
import com.lexor.vehicle.security.UserPrincipal;
import com.lexor.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver Vehicle Management", description = "Endpoints for drivers to register, inspect, update, and submit their vehicles for verification")
@SecurityRequirement(name = "BearerAuthentication")
public class DriverVehicleController {

    private final VehicleService vehicleService;

    @Operation(summary = "Register a vehicle", description = "Registers a new vehicle under the authenticated driver's account. Vehicle defaults to PENDING_VERIFICATION.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vehicle registered successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation failure or invalid data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role with driver profile",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - Duplicate license plate or VIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<VehicleResponse> registerVehicle(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody RegisterVehicleRequest request
    ) {
        VehicleResponse response = vehicleService.registerVehicle(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List authenticated driver's vehicles", description = "Retrieves all vehicles registered under the authenticated driver's profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicles retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<VehicleResponse>> getMyVehicles(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<VehicleResponse> vehicles = vehicleService.getMyVehicles(currentUser);
        return ResponseEntity.ok(vehicles);
    }

    @Operation(summary = "Get vehicle details by UUID", description = "Retrieves details of a vehicle owned by the authenticated driver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle retrieved successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied to other drivers' vehicles",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}")
    public ResponseEntity<VehicleResponse> getVehicleByUuid(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid
    ) {
        VehicleResponse response = vehicleService.getVehicleByUuid(currentUser, vehicleUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update permitted vehicle details", description = "Updates details of a registered vehicle (color, year, category, etc.).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle updated successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation or invalid status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{vehicleUuid}")
    public ResponseEntity<VehicleResponse> updateVehicle(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        VehicleResponse response = vehicleService.updateVehicle(currentUser, vehicleUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Toggle vehicle active status", description = "Activates or deactivates a vehicle. Vehicle must be VERIFIED to be activated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle status updated successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Cannot activate unverified vehicle",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{vehicleUuid}/status")
    public ResponseEntity<VehicleResponse> updateVehicleStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid,
            @Valid @RequestBody VehicleStatusUpdateRequest request
    ) {
        VehicleResponse response = vehicleService.updateVehicleStatus(currentUser, vehicleUuid, request.getIsActive());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Submit vehicle for administrative verification", description = "Submits a vehicle and its uploaded documents for administrative review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vehicle submitted for verification",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Missing mandatory documents or invalid state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{vehicleUuid}/submit-verification")
    public ResponseEntity<VehicleResponse> submitForVerification(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid
    ) {
        VehicleResponse response = vehicleService.submitForVerification(currentUser, vehicleUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "View verification status and checklist", description = "Retrieves the verification checklist, missing documents, and eligibility status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification checklist retrieved successfully",
                    content = @Content(schema = @Schema(implementation = VehicleVerificationStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/verification-status")
    public ResponseEntity<VehicleVerificationStatusResponse> getVerificationStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid
    ) {
        VehicleVerificationStatusResponse response = vehicleService.getVerificationStatus(currentUser, vehicleUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "View vehicle verification history", description = "Retrieves administrative audit decisions for this vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/verification-history")
    public ResponseEntity<List<VehicleVerificationHistoryResponse>> getVerificationHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid
    ) {
        List<VehicleVerificationHistoryResponse> response = vehicleService.getVehicleVerificationHistory(currentUser, vehicleUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "View vehicle driver assignments", description = "Retrieves driver assignment history for this vehicle.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Assignment history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{vehicleUuid}/assignments")
    public ResponseEntity<List<DriverVehicleAssignmentResponse>> getVehicleAssignments(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Vehicle UUID", required = true)
            @PathVariable String vehicleUuid
    ) {
        List<DriverVehicleAssignmentResponse> response = vehicleService.getVehicleAssignments(currentUser, vehicleUuid);
        return ResponseEntity.ok(response);
    }
}
