package com.lexor.riderequest.controller;

import com.lexor.riderequest.dto.response.ErrorResponse;
import com.lexor.riderequest.dto.response.PageResponse;
import com.lexor.riderequest.dto.response.RideRequestResponse;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.service.AdminRideRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ride-requests")
@RequiredArgsConstructor
@Tag(name = "Administrator Ride Requests", description = "Administrative operations for monitoring, auditing, and querying platform ride requests")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRideRequestController {

    private final AdminRideRequestService adminRideRequestService;

    @Operation(
            summary = "Admin: List all ride requests",
            description = "Retrieves a paginated list of all ride requests across the platform with optional filters for status and rider profile ID. Requires administrator privileges."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride requests retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller does not have ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<PageResponse<RideRequestResponse>> getAllRideRequests(
            @Parameter(description = "Optional filter by request status")
            @RequestParam(required = false) RideRequestStatus status,
            @Parameter(description = "Optional filter by rider profile ID")
            @RequestParam(required = false) Long riderId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<RideRequestResponse> response = adminRideRequestService.getAllRideRequests(pageable, status, riderId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Admin: Get ride request by UUID",
            description = "Retrieves details of any ride request on the platform by its public UUID identifier. Requires administrator privileges."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride request retrieved successfully",
                    content = @Content(schema = @Schema(implementation = RideRequestResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller does not have ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{requestUuid}")
    public ResponseEntity<RideRequestResponse> getRideRequestByUuid(
            @Parameter(description = "Public UUID identifier of the ride request", required = true)
            @PathVariable String requestUuid
    ) {
        RideRequestResponse response = adminRideRequestService.getRideRequestByUuid(requestUuid);
        return ResponseEntity.ok(response);
    }
}
