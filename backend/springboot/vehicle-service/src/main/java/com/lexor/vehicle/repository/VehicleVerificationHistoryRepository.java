package com.lexor.vehicle.repository;

import com.lexor.vehicle.entity.VehicleVerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleVerificationHistoryRepository extends JpaRepository<VehicleVerificationHistory, Long> {

    List<VehicleVerificationHistory> findByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    List<VehicleVerificationHistory> findByVehicleUuidOrderByCreatedAtDesc(String vehicleUuid);
}
