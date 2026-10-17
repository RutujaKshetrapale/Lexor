package com.lexor.matching.repository;

import com.lexor.matching.entity.MatchingAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchingAttemptRepository extends JpaRepository<MatchingAttempt, Long> {

    Optional<MatchingAttempt> findByAttemptUuid(String attemptUuid);

    List<MatchingAttempt> findByRideRequestUuidOrderByAttemptNumberDesc(String rideRequestUuid);

    Optional<MatchingAttempt> findFirstByRideRequestUuidOrderByAttemptNumberDesc(String rideRequestUuid);

    @Query("SELECT COUNT(m) FROM MatchingAttempt m WHERE m.rideRequestUuid = :rideRequestUuid")
    int countByRideRequestUuid(@Param("rideRequestUuid") String rideRequestUuid);
}
