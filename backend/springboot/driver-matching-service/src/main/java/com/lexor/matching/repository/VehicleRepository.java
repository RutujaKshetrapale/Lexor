package com.lexor.matching.repository;

import com.lexor.matching.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByUuid(String uuid);

    List<Vehicle> findByDriverId(Long driverId);
}
