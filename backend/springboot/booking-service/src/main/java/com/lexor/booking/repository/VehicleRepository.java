package com.lexor.booking.repository;

import com.lexor.booking.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByUuid(String uuid);

    Optional<Vehicle> findFirstByDriverIdAndIsActiveTrue(Long driverId);
}
