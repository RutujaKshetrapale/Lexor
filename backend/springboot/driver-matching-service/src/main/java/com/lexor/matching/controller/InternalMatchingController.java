package com.lexor.matching.controller;

import com.lexor.matching.dto.request.MatchingRetryRequest;
import com.lexor.matching.dto.response.ErrorResponse;
import com.lexor.matching.dto.response.MatchingAttemptResponse;
import com.lexor.matching.service.DriverMatchingService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/matching/ride-requests")
@RequiredArgsConstructor
@Tag(name = "Internal Driver Matching", description = "Protected internal APIs for initiating and retrying driver matching workflows")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('ADMIN')")
public class InternalMatchingController {

    private final DriverMatchingService driverMatchingService;

    @Operation(
            summary = "Initiate driver matching attempt",
            description = "Triggers the matching engine to find, rank, and reserve an eligible driver candidate for a submitted ride request. Requires internal service or administrator authorization."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Matching attempt evaluated successfully (check status for MATCHED or NO_DRIVERS_FOUND)",
                    content = @Content(schema = @Schema(implementation = MatchingAttemptResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT or service credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller lacks internal matching authorization (ROLE_ADMIN or internal key required)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Ride request lifecycle status prevents matching (e.g. cancelled or expired)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{requestUuid}/attempts")
    public ResponseEntity<MatchingAttemptResponse> initiateMatching(
            @Parameter(description = "Public UUID identifier of the ride request", required = true)
            @PathVariable String requestUuid,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        MatchingAttemptResponse response = driverMatchingService.initiateMatching(requestUuid, correlationId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get matching attempt status",
            description = "Retrieves the latest matching attempt and reservation status for a ride request."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Matching status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MatchingAttemptResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT or service credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires internal authorization",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No matching attempt found for ride request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{requestUuid}")
    public ResponseEntity<MatchingAttemptResponse> getMatchingStatus(
            @Parameter(description = "Public UUID identifier of the ride request", required = true)
            @PathVariable String requestUuid
    ) {
        MatchingAttemptResponse response = driverMatchingService.getMatchingStatus(requestUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Retry matching attempt",
            description = "Retries matching for a ride request that previously yielded NO_DRIVERS_FOUND or failed, provided the request lifecycle status permits it."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Matching retry initiated and resolved",
                    content = @Content(schema = @Schema(implementation = MatchingAttemptResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires internal authorization",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ride request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Request cannot be retried (e.g. already matched or cancelled)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{requestUuid}/retry")
    public ResponseEntity<MatchingAttemptResponse> retryMatching(
            @Parameter(description = "Public UUID identifier of the ride request", required = true)
            @PathVariable String requestUuid,
            @Valid @RequestBody(required = false) MatchingRetryRequest retryRequest
    ) {
        MatchingAttemptResponse response = driverMatchingService.retryMatching(
                requestUuid,
                retryRequest != null ? retryRequest : new MatchingRetryRequest()
        );
        return ResponseEntity.ok(response);
    }
}
