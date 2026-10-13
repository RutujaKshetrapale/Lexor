package com.lexor.vehicle.repository;

import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByUuid(String uuid);

    Optional<Vehicle> findByLicensePlate(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByLicensePlateAndIdNot(String licensePlate, Long id);

    boolean existsByVin(String vin);

    boolean existsByVinAndIdNot(String vin, Long id);

    List<Vehicle> findByDriverId(Long driverId);

    @Query("SELECT v FROM Vehicle v WHERE v.driver.user.uuid = :userUuid")
    List<Vehicle> findByDriverUserUuid(@Param("userUuid") String userUuid);

    Page<Vehicle> findByVerificationStatus(VehicleVerificationStatus status, Pageable pageable);

    long countByVerificationStatus(VehicleVerificationStatus status);
}
