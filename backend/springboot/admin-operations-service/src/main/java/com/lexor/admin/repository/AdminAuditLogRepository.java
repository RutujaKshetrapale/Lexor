package com.lexor.admin.repository;

import com.lexor.admin.domain.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Optional<AdminAuditLog> findByAuditUuid(String auditUuid);

    Page<AdminAuditLog> findByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a FROM AdminAuditLog a WHERE " +
           "(:adminUserUuid IS NULL OR a.adminUserUuid = :adminUserUuid) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> searchAuditLogs(
            @Param("adminUserUuid") String adminUserUuid,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
