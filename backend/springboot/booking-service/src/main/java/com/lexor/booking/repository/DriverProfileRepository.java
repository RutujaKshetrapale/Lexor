package com.lexor.booking.repository;

import com.lexor.booking.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    @Query("SELECT dp FROM DriverProfile dp JOIN dp.user u WHERE u.uuid = :userUuid")
    Optional<DriverProfile> findByUserUuid(@Param("userUuid") String userUuid);

    @Query("SELECT dp FROM DriverProfile dp WHERE dp.user.id = :userId")
    Optional<DriverProfile> findByUserId(@Param("userId") Long userId);
}
