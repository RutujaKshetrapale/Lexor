package com.lexor.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogResponse {

    private String auditUuid;
    private String adminUserUuid;
    private String action;
    private String resourceType;
    private String resourceUuid;
    private String reason;
    private String details;
    private String ipAddress;
    private String status;
    private LocalDateTime createdAt;
}
