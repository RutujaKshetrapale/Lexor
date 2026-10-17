package com.lexor.matching.repository;

import com.lexor.matching.entity.RideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    Optional<RideRequest> findByUuid(String uuid);
}
