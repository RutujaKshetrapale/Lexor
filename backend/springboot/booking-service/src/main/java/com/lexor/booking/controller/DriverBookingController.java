package com.lexor.booking.controller;

import com.lexor.booking.dto.request.CompleteTripRequest;
import com.lexor.booking.dto.request.DeclineBookingRequest;
import com.lexor.booking.dto.request.StartTripRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers/me/bookings")
@RequiredArgsConstructor
@Tag(name = "Driver Bookings & Trips", description = "Endpoints for assigned drivers to review assigned bookings and manage journey lifecycle states")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('DRIVER')")
public class DriverBookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "List driver's assigned bookings",
            description = "Retrieves a paginated list of bookings assigned to the authenticated driver, optionally filtered by status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Driver bookings retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Requires ROLE_DRIVER",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<PageResponse<BookingResponse>> getDriverBookings(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Optional filter by status")
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BookingResponse> response = bookingService.getDriverBookings(driverPrincipal, status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get assigned booking by UUID",
            description = "Retrieves details of an assigned booking. Enforces driver assignment ownership."
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
                    description = "Forbidden - Booking not assigned to caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{bookingUuid}")
    public ResponseEntity<BookingResponse> getDriverBookingByUuid(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid
    ) {
        BookingResponse response = bookingService.getDriverBookingByUuid(driverPrincipal, bookingUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Accept offered booking",
            description = "Driver accepts an offered ride booking, confirming the reservation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking accepted successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller is not the assigned driver",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Booking is cancelled or no longer in an acceptable state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{bookingUuid}/accept")
    public ResponseEntity<BookingResponse> acceptBooking(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid
    ) {
        BookingResponse response = bookingService.acceptBookingByDriver(driverPrincipal, bookingUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Decline offered booking",
            description = "Driver declines an offered booking, releasing the reservation concurrency lock."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking declined successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller is not the assigned driver",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Booking already in progress or completed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{bookingUuid}/decline")
    public ResponseEntity<BookingResponse> declineBooking(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid,
            @Valid @RequestBody(required = false) DeclineBookingRequest request
    ) {
        BookingResponse response = bookingService.declineBookingByDriver(driverPrincipal, bookingUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Record driver arrival at pickup",
            description = "Records that the assigned driver has physically arrived at the pickup location."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Arrival recorded successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller is not the assigned driver",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Booking is not in an accepted or confirmed state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{bookingUuid}/arrived")
    public ResponseEntity<BookingResponse> recordArrival(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid
    ) {
        BookingResponse response = bookingService.arriveBookingByDriver(driverPrincipal, bookingUuid);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Start trip with rider",
            description = "Transitions the booking into IN_PROGRESS trip state. Optionally verifies the rider's 4-digit OTP."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Trip started successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - Incorrect OTP provided",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller is not the assigned driver",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Booking is not in an arrived or accepted state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{bookingUuid}/start")
    public ResponseEntity<BookingResponse> startTrip(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid,
            @Valid @RequestBody(required = false) StartTripRequest request
    ) {
        BookingResponse response = bookingService.startTripByDriver(driverPrincipal, bookingUuid, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Complete trip",
            description = "Concludes the journey, marking the booking COMPLETED and recording final distance and duration metrics. Operation is strictly idempotent."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Trip completed successfully",
                    content = @Content(schema = @Schema(implementation = BookingResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Caller is not the assigned driver",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict - Trip is not currently IN_PROGRESS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/{bookingUuid}/complete")
    public ResponseEntity<BookingResponse> completeTrip(
            @AuthenticationPrincipal UserPrincipal driverPrincipal,
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid,
            @Valid @RequestBody(required = false) CompleteTripRequest request
    ) {
        BookingResponse response = bookingService.completeTripByDriver(driverPrincipal, bookingUuid, request);
        return ResponseEntity.ok(response);
    }
}
