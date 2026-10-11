package com.lexor.driver.repository;

import com.lexor.driver.entity.DriverProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long>, JpaSpecificationExecutor<DriverProfile> {

    Optional<DriverProfile> findByUserId(Long userId);

    @Query("SELECT dp FROM DriverProfile dp JOIN FETCH dp.user u WHERE u.uuid = :userUuid")
    Optional<DriverProfile> findByUserUuid(@Param("userUuid") String userUuid);

    Optional<DriverProfile> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);
}
