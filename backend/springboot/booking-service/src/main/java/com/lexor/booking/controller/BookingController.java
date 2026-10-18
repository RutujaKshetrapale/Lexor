package com.lexor.booking.controller;

import com.lexor.booking.dto.request.CancelBookingRequest;
import com.lexor.booking.dto.request.CreateBookingRequest;
import com.lexor.booking.dto.response.BookingResponse;
import com.lexor.booking.dto.response.ErrorResponse;
import com.lexor.booking.dto.response.PageResponse;
import com.lexor.booking.entity.BookingStatus;
import com.lexor.booking.security.UserPrincipal;
import com.lexor.booking.service.BookingService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Rider Bookings", description = "Endpoints for riders to create bookings, query personal journey records, and cancel active bookings")
@SecurityRequirement(name = "BearerAuthentication")
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Create booking from ride request",
            description = "Creates a confirmed booking and reserves the matched driver/vehicle for the authenticated rider's valid ride request. Issues a secure 4-digit OTP."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Booking created successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Missing parameters or validation failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
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
                    description = "Conflict - Request already booked, cancelled, or no eligible driver available",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        BookingResponse response = bookingService.createBooking(riderPrincipal, request, authorizationHeader, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List authenticated rider's bookings",
            description = "Retrieves a paginated list of bookings belonging exclusively to the currently authenticated rider, sorted by creation date."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Rider bookings retrieved successfully"
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
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<PageResponse<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Parameter(description = "Optional filter by booking lifecycle status")
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BookingResponse> response = bookingService.getMyBookings(riderPrincipal, status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get rider's booking by UUID",
            description = "Retrieves details of a specific booking owned by the authenticated rider. Enforces strict user ownership."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Booking belongs to another user",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/me/{bookingUuid}")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<BookingResponse> getMyBookingByUuid(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid
    ) {
        BookingResponse response = bookingService.getMyBookingByUuid(riderPrincipal, bookingUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Cancel booking by rider",
            description = "Cancels an active booking owned by the authenticated rider if its lifecycle permits cancellation. Completed or in-progress trips cannot be cancelled."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking cancelled successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Booking belongs to another user or caller lacks ROLE_RIDER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Booking is already completed or in-progress",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{bookingUuid}/cancel")
    @PreAuthorize("hasRole('RIDER') or hasRole('ADMIN')")
    public ResponseEntity<BookingResponse> cancelBooking(
            @AuthenticationPrincipal UserPrincipal riderPrincipal,
            @Parameter(description = "Public UUID identifier of the booking to cancel", required = true)
            @PathVariable String bookingUuid,
            @Valid @RequestBody(required = false) CancelBookingRequest request
    ) {
        BookingResponse response = bookingService.cancelBookingByRider(riderPrincipal, bookingUuid, request);
        return ResponseEntity.ok(response);
    }
}
