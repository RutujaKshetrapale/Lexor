package com.lexor.admin.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "admin_audit_logs",
        indexes = {
                @Index(name = "idx_audit_admin_uuid", columnList = "admin_user_uuid"),
                @Index(name = "idx_audit_action", columnList = "action"),
                @Index(name = "idx_audit_resource", columnList = "resource_type, resource_uuid"),
                @Index(name = "idx_audit_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_uuid", nullable = false, unique = true, length = 36)
    private String auditUuid;

    @Column(name = "admin_user_uuid", nullable = false, length = 36)
    private String adminUserUuid;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "resource_uuid", length = 36)
    private String resourceUuid;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "SUCCESS";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
