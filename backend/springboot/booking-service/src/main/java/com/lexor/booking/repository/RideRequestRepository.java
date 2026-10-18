package com.lexor.booking.repository;

import com.lexor.booking.entity.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    Optional<RideRequest> findByUuid(String uuid);
}
