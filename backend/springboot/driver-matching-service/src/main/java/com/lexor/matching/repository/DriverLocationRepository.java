package com.lexor.matching.repository;

import com.lexor.matching.entity.DriverLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

    Optional<DriverLocation> findFirstByDriverIdOrderByRecordedAtDesc(Long driverId);

    @Query("SELECT dl FROM DriverLocation dl WHERE dl.driver.id = :driverId ORDER BY dl.recordedAt DESC LIMIT 1")
    Optional<DriverLocation> findLatestByDriverId(@Param("driverId") Long driverId);
}
