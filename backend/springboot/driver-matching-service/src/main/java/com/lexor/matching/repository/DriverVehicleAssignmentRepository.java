package com.lexor.matching.repository;

import com.lexor.matching.entity.AssignmentStatus;
import com.lexor.matching.entity.DriverVehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverVehicleAssignmentRepository extends JpaRepository<DriverVehicleAssignment, Long> {

    Optional<DriverVehicleAssignment> findFirstByDriverIdAndStatus(Long driverId, AssignmentStatus status);

    Optional<DriverVehicleAssignment> findFirstByVehicleIdAndStatus(Long vehicleId, AssignmentStatus status);
}
