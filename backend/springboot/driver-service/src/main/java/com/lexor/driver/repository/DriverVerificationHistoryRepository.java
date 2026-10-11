package com.lexor.driver.repository;

import com.lexor.driver.entity.DriverVerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverVerificationHistoryRepository extends JpaRepository<DriverVerificationHistory, Long> {

    List<DriverVerificationHistory> findByDriverIdOrderByCreatedAtDesc(Long driverId);

    @Query("SELECT h FROM DriverVerificationHistory h JOIN FETCH h.driver dp JOIN FETCH dp.user u WHERE u.uuid = :driverUuid ORDER BY h.createdAt DESC")
    List<DriverVerificationHistory> findByDriverUserUuidOrderByCreatedAtDesc(@Param("driverUuid") String driverUuid);
}
