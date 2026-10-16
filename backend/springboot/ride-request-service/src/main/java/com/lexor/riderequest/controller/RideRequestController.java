package com.lexor.riderequest.controller;

import com.lexor.riderequest.dto.request.CancelRideRequest;
import com.lexor.riderequest.dto.request.CreateRideRequest;
import com.lexor.riderequest.dto.response.ErrorResponse;
import com.lexor.riderequest.dto.response.PageResponse;
import com.lexor.riderequest.dto.response.RideRequestResponse;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.security.UserPrincipal;
import com.lexor.riderequest.service.RideRequestService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ride-requests")
@RequiredArgsConstructor
@Tag(name = "Rider Ride Requests", description = "Rider-facing endpoints to submit ride requests, calculate fares, track own requests, and cancel bookings")
@SecurityRequirement(name = "BearerAuthentication")
public class RideRequestController {

    private final RideRequestService rideRequestService;

    @Operation(
            summary = "Create ride request",
            description = "Submits a new ride request with pickup, destination, and selected ride category. Calculates estimated distance, duration, and fare. Rider identity is strictly derived from the validated security context."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Ride request created successfully",
                    content = @Content(schema = @Schema(implementation = RideRequestResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Invalid coordinates, identical pickup/drop locations, or disabled category",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller does not have ROLE_RIDER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride category not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<RideRequestResponse> createRideRequest(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Valid @RequestBody CreateRideRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        RideRequestResponse response = rideRequestService.createRideRequest(riderPrincipal, request, authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List authenticated rider's requests",
            description = "Retrieves a paginated list of ride requests belonging exclusively to the currently authenticated rider, sorted by request date."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Rider requests retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ROLE_RIDER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public ResponseEntity<PageResponse<RideRequestResponse>> getMyRideRequests(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Parameter(description = "Optional status filter (e.g. REQUESTED, SEARCHING, MATCHED, CANCELLED, EXPIRED)")
            @RequestParam(required = false) RideRequestStatus status,
            @PageableDefault(size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<RideRequestResponse> response = rideRequestService.getMyRideRequests(riderPrincipal, pageable, status);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get rider's request details by UUID",
            description = "Retrieves details of a specific ride request. Enforces ownership: only returns the request if it belongs to the authenticated rider."
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
                    description = "Forbidden - Request belongs to another rider or caller lacks ROLE_RIDER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me/{requestUuid}")
    public ResponseEntity<RideRequestResponse> getMyRideRequestByUuid(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Parameter(description = "Public UUID identifier of the ride request", required = true)
            @PathVariable String requestUuid
    ) {
        RideRequestResponse response = rideRequestService.getMyRideRequestByUuid(riderPrincipal, requestUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Cancel authenticated rider's ride request",
            description = "Cancels a ride request submitted by the authenticated rider if its lifecycle status allows cancellation (REQUESTED or SEARCHING). Enforces ownership and prevents illegal transitions."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ride request cancelled successfully",
                    content = @Content(schema = @Schema(implementation = RideRequestResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Request belongs to another rider or caller lacks ROLE_RIDER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Request cannot be cancelled in current lifecycle status (already MATCHED or CANCELLED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{requestUuid}/cancel")
    public ResponseEntity<RideRequestResponse> cancelRideRequest(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Parameter(description = "Public UUID identifier of the ride request to cancel", required = true)
            @PathVariable String requestUuid,
            @Valid @RequestBody(required = false) CancelRideRequest request
    ) {
        RideRequestResponse response = rideRequestService.cancelRideRequest(riderPrincipal, requestUuid, request);
        return ResponseEntity.ok(response);
    }
}
