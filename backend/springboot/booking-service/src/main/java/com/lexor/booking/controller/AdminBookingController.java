package com.lexor.booking.controller;

import com.lexor.booking.dto.response.BookingResponse;
import com.lexor.booking.dto.response.ErrorResponse;
import com.lexor.booking.dto.response.PageResponse;
import com.lexor.booking.entity.BookingStatus;
import com.lexor.booking.service.BookingService;
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
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "Administrator Bookings", description = "Administrative operations for monitoring, auditing, and querying platform bookings")
@SecurityRequirement(name = "BearerAuthentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Admin: List all bookings",
            description = "Retrieves a paginated list of all bookings across the platform with optional filters for status, rider, and driver. Requires administrator privileges."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Bookings retrieved successfully"
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
    public ResponseEntity<PageResponse<BookingResponse>> getAllBookings(
            @Parameter(description = "Optional filter by rider profile ID")
            @RequestParam(required = false) Long riderId,
            @Parameter(description = "Optional filter by driver profile ID")
            @RequestParam(required = false) Long driverId,
            @Parameter(description = "Optional filter by booking status")
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<BookingResponse> response = bookingService.getAdminBookings(riderId, driverId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Admin: Get booking details by UUID",
            description = "Retrieves complete details of any booking on the platform by its UUID. Requires administrator privileges."
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
                    description = "Forbidden - Caller does not have ROLE_ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{bookingUuid}")
    public ResponseEntity<BookingResponse> getBookingByUuid(
            @Parameter(description = "Public UUID identifier of the booking", required = true)
            @PathVariable String bookingUuid
    ) {
        BookingResponse response = bookingService.getAdminBookingByUuid(bookingUuid);
        return ResponseEntity.ok(response);
    }
}
