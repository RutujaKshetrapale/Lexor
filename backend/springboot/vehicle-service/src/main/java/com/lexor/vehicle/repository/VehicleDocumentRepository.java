package com.lexor.vehicle.repository;

import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

    List<VehicleDocument> findByVehicleIdAndIsCurrentTrue(Long vehicleId);

    List<VehicleDocument> findByVehicleId(Long vehicleId);

    Optional<VehicleDocument> findByIdAndVehicleId(Long id, Long vehicleId);

    Optional<VehicleDocument> findByVehicleIdAndDocumentTypeAndIsCurrentTrue(Long vehicleId, VehicleDocumentType documentType);

    Optional<VehicleDocument> findTopByVehicleIdAndDocumentTypeOrderByVersionDesc(Long vehicleId, VehicleDocumentType documentType);
}
