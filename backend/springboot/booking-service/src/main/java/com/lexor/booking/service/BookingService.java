package com.lexor.booking.service;

import com.lexor.booking.dto.request.CancelBookingRequest;
import com.lexor.booking.dto.request.CompleteTripRequest;
import com.lexor.booking.dto.request.CreateBookingRequest;
import com.lexor.booking.dto.request.DeclineBookingRequest;
import com.lexor.booking.dto.request.StartTripRequest;
import com.lexor.booking.dto.response.BookingResponse;
import com.lexor.booking.dto.response.PageResponse;
import com.lexor.booking.entity.BookingStatus;
import com.lexor.booking.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

public interface BookingService {

    // Rider operations
    BookingResponse createBooking(UserPrincipal riderPrincipal, CreateBookingRequest request, String bearerToken, String correlationId);

    PageResponse<BookingResponse> getMyBookings(UserPrincipal riderPrincipal, BookingStatus status, Pageable pageable);

    BookingResponse getMyBookingByUuid(UserPrincipal riderPrincipal, String bookingUuid);

    BookingResponse cancelBookingByRider(UserPrincipal riderPrincipal, String bookingUuid, CancelBookingRequest request);

    // Driver operations
    PageResponse<BookingResponse> getDriverBookings(UserPrincipal driverPrincipal, BookingStatus status, Pageable pageable);

    BookingResponse getDriverBookingByUuid(UserPrincipal driverPrincipal, String bookingUuid);

    BookingResponse acceptBookingByDriver(UserPrincipal driverPrincipal, String bookingUuid);

    BookingResponse declineBookingByDriver(UserPrincipal driverPrincipal, String bookingUuid, DeclineBookingRequest request);

    BookingResponse arriveBookingByDriver(UserPrincipal driverPrincipal, String bookingUuid);

    BookingResponse startTripByDriver(UserPrincipal driverPrincipal, String bookingUuid, StartTripRequest request);

    BookingResponse completeTripByDriver(UserPrincipal driverPrincipal, String bookingUuid, CompleteTripRequest request);

    // Admin operations
    PageResponse<BookingResponse> getAdminBookings(Long riderId, Long driverId, BookingStatus status, Pageable pageable);

    BookingResponse getAdminBookingByUuid(String bookingUuid);
}
