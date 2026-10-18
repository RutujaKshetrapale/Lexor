package com.lexor.booking.repository;

import com.lexor.booking.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    Optional<Ride> findByUuid(String uuid);

    Optional<Ride> findByBookingId(Long bookingId);
}
