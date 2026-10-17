package com.lexor.matching.repository;

import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.entity.DriverStatus;
import com.lexor.matching.entity.OnboardingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    Optional<DriverProfile> findByUserId(Long userId);

    @Query("SELECT dp FROM DriverProfile dp JOIN dp.user u WHERE u.uuid = :userUuid")
    Optional<DriverProfile> findByUserUuid(@Param("userUuid") String userUuid);

    @Query("SELECT dp FROM DriverProfile dp JOIN dp.user u " +
           "WHERE dp.status = :status " +
           "AND dp.onboardingStatus = :onboardingStatus " +
           "AND dp.isVerified = true " +
           "AND u.status = 'ACTIVE'")
    List<DriverProfile> findEligibleOnlineDrivers(
            @Param("status") DriverStatus status,
            @Param("onboardingStatus") OnboardingStatus onboardingStatus
    );
}
