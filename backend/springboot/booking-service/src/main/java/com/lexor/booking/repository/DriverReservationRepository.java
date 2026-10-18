package com.lexor.booking.repository;

import com.lexor.booking.entity.DriverReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DriverReservationRepository extends JpaRepository<DriverReservation, Long> {

    Optional<DriverReservation> findByReservationUuid(String reservationUuid);

    @Query("SELECT r FROM DriverReservation r WHERE r.rideRequest.id = :rideRequestId AND (r.status = 'CONFIRMED' OR (r.status = 'RESERVED' AND r.expiresAt > :now))")
    Optional<DriverReservation> findActiveReservationForRideRequest(@Param("rideRequestId") Long rideRequestId, @Param("now") LocalDateTime now);

    @Query("SELECT r FROM DriverReservation r WHERE r.driver.id = :driverId AND (r.status = 'CONFIRMED' OR (r.status = 'RESERVED' AND r.expiresAt > :now))")
    List<DriverReservation> findActiveReservationsForDriver(@Param("driverId") Long driverId, @Param("now") LocalDateTime now);

    List<DriverReservation> findByRideRequestId(Long rideRequestId);
}
