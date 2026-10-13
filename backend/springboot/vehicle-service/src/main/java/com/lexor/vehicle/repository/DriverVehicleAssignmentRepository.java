package com.lexor.vehicle.repository;

import com.lexor.vehicle.entity.AssignmentStatus;
import com.lexor.vehicle.entity.DriverVehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverVehicleAssignmentRepository extends JpaRepository<DriverVehicleAssignment, Long> {

    List<DriverVehicleAssignment> findByVehicleIdOrderByAssignedAtDesc(Long vehicleId);

    List<DriverVehicleAssignment> findByVehicleUuidOrderByAssignedAtDesc(String vehicleUuid);

    Optional<DriverVehicleAssignment> findByVehicleIdAndStatus(Long vehicleId, AssignmentStatus status);

    List<DriverVehicleAssignment> findByDriverIdAndStatus(Long driverId, AssignmentStatus status);

    boolean existsByVehicleIdAndStatus(Long vehicleId, AssignmentStatus status);

    boolean existsByDriverIdAndStatus(Long driverId, AssignmentStatus status);
}
