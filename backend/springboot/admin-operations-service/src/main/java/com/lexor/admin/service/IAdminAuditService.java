package com.lexor.admin.service;

import com.lexor.admin.dto.request.RecordAuditLogRequest;
import com.lexor.admin.dto.request.UpdateSettingRequest;
import com.lexor.admin.dto.response.AdminAuditLogResponse;
import com.lexor.admin.dto.response.AdminSettingResponse;
import com.lexor.admin.dto.response.PageResponse;
import com.lexor.admin.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface IAdminAuditService {

    AdminAuditLogResponse recordAuditLog(UserPrincipal admin, RecordAuditLogRequest request, String ipAddress);

    PageResponse<AdminAuditLogResponse> getAuditLogs(
            String adminUserUuid,
            String action,
            String resourceType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    List<AdminSettingResponse> getAllSettings();

    AdminSettingResponse updateSetting(UserPrincipal admin, UpdateSettingRequest request);
}
