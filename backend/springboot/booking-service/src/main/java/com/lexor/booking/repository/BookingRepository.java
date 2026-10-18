package com.lexor.booking.repository;

import com.lexor.booking.entity.Booking;
import com.lexor.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByUuid(String uuid);

    Optional<Booking> findByRideRequestId(Long rideRequestId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Page<Booking> findByRiderId(Long riderId, Pageable pageable);

    Page<Booking> findByRiderIdAndBookingStatus(Long riderId, BookingStatus bookingStatus, Pageable pageable);

    Page<Booking> findByDriverId(Long driverId, Pageable pageable);

    Page<Booking> findByDriverIdAndBookingStatus(Long driverId, BookingStatus bookingStatus, Pageable pageable);

    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.driver.id = :driverId AND b.bookingStatus IN :statuses")
    boolean hasActiveBookingForDriver(@Param("driverId") Long driverId, @Param("statuses") Collection<BookingStatus> statuses);

    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.rideRequest.id = :rideRequestId")
    boolean existsByRideRequestId(@Param("rideRequestId") Long rideRequestId);
}
