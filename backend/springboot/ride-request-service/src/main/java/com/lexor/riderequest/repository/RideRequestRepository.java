package com.lexor.riderequest.repository;

import com.lexor.riderequest.entity.RideRequest;
import com.lexor.riderequest.entity.RideRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RideRequestRepository extends JpaRepository<RideRequest, Long>, JpaSpecificationExecutor<RideRequest> {

    @Query("SELECT r FROM RideRequest r " +
           "JOIN FETCH r.rider rp " +
           "JOIN FETCH rp.user u " +
           "JOIN FETCH r.rideType rt " +
           "WHERE r.uuid = :uuid")
    Optional<RideRequest> findByUuid(@Param("uuid") String uuid);

    @Query(value = "SELECT r FROM RideRequest r " +
                   "JOIN FETCH r.rider rp " +
                   "JOIN FETCH rp.user u " +
                   "JOIN FETCH r.rideType rt " +
                   "WHERE rp.id = :riderId",
           countQuery = "SELECT COUNT(r) FROM RideRequest r WHERE r.rider.id = :riderId")
    Page<RideRequest> findByRiderId(@Param("riderId") Long riderId, Pageable pageable);

    @Query(value = "SELECT r FROM RideRequest r " +
                   "JOIN FETCH r.rider rp " +
                   "JOIN FETCH rp.user u " +
                   "JOIN FETCH r.rideType rt " +
                   "WHERE rp.id = :riderId AND r.status = :status",
           countQuery = "SELECT COUNT(r) FROM RideRequest r WHERE r.rider.id = :riderId AND r.status = :status")
    Page<RideRequest> findByRiderIdAndStatus(@Param("riderId") Long riderId,
                                            @Param("status") RideRequestStatus status,
                                            Pageable pageable);

    @Query(value = "SELECT r FROM RideRequest r " +
                   "JOIN FETCH r.rider rp " +
                   "JOIN FETCH rp.user u " +
                   "JOIN FETCH r.rideType rt " +
                   "WHERE r.status = :status",
           countQuery = "SELECT COUNT(r) FROM RideRequest r WHERE r.status = :status")
    Page<RideRequest> findByStatus(@Param("status") RideRequestStatus status, Pageable pageable);

    long countByStatus(RideRequestStatus status);
}
