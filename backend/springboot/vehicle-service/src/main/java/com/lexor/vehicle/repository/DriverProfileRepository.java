package com.lexor.vehicle.repository;

import com.lexor.vehicle.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    @Query("SELECT dp FROM DriverProfile dp WHERE dp.user.id = :userId")
    Optional<DriverProfile> findByUserId(@Param("userId") Long userId);

    @Query("SELECT dp FROM DriverProfile dp WHERE dp.user.uuid = :userUuid")
    Optional<DriverProfile> findByUserUuid(@Param("userUuid") String userUuid);
}
