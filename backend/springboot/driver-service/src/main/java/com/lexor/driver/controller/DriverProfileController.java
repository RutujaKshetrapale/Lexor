package com.lexor.driver.controller;

import com.lexor.driver.dto.request.DriverAvailabilityRequest;
import com.lexor.driver.dto.request.PatchDriverProfileRequest;
import com.lexor.driver.dto.request.UpdateDriverProfileRequest;
import com.lexor.driver.dto.response.DriverAvailabilityResponse;
import com.lexor.driver.dto.response.DriverOnboardingResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.dto.response.ErrorResponse;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.DriverProfileService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
@Tag(name = "Driver Profile & Onboarding", description = "Endpoints for authenticated drivers to manage profile, onboarding, and availability")
@SecurityRequirement(name = "BearerAuthentication")
public class DriverProfileController {

    private final DriverProfileService driverProfileService;

    @Operation(summary = "Get current driver profile", description = "Retrieves the authenticated driver's profile, including identity, license, and address details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver profile not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<DriverProfileResponse> getCurrentDriverProfile(@AuthenticationPrincipal UserPrincipal currentUser) {
        DriverProfileResponse response = driverProfileService.getDriverProfile(currentUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fully update driver profile", description = "Fully updates permitted profile fields (license details, address, date of birth, emergency contact). Protected identity and role fields are guarded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error or invalid dates",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - License number already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<DriverProfileResponse> updateDriverProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateDriverProfileRequest request
    ) {
        DriverProfileResponse response = driverProfileService.updateDriverProfile(currentUser, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Partially update driver profile", description = "Partially updates supplied profile fields without clearing omitted values.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile patched successfully",
                    content = @Content(schema = @Schema(implementation = DriverProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - License number already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping
    public ResponseEntity<DriverProfileResponse> patchDriverProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody PatchDriverProfileRequest request
    ) {
        DriverProfileResponse response = driverProfileService.patchDriverProfile(currentUser, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get onboarding progress and checklist", description = "Retrieves completion score, required document checklist, outstanding onboarding tasks, and eligibility.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Onboarding status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DriverOnboardingResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/onboarding")
    public ResponseEntity<DriverOnboardingResponse> getOnboardingStatus(@AuthenticationPrincipal UserPrincipal currentUser) {
        DriverOnboardingResponse response = driverProfileService.getOnboardingStatus(currentUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Submit profile for verification review", description = "Submits driver profile and uploaded documents for administrative review once all prerequisites are met.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile submitted for review successfully",
                    content = @Content(schema = @Schema(implementation = DriverOnboardingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Prerequisites not satisfied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires DRIVER role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/submit-for-review")
    public ResponseEntity<DriverOnboardingResponse> submitForReview(@AuthenticationPrincipal UserPrincipal currentUser) {
        DriverOnboardingResponse response = driverProfileService.submitForReview(currentUser);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update driver availability status", description = "Toggles driver availability between ONLINE and OFFLINE. Going online requires a fully VERIFIED onboarding state and ACTIVE account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Availability updated successfully",
                    content = @Content(schema = @Schema(implementation = DriverAvailabilityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid status requested",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Driver is not verified or account is inactive",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/availability")
    public ResponseEntity<DriverAvailabilityResponse> updateAvailability(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody DriverAvailabilityRequest request
    ) {
        DriverAvailabilityResponse response = driverProfileService.updateAvailability(currentUser, request);
        return ResponseEntity.ok(response);
    }
}
