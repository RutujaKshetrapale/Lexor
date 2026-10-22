package com.lexor.admin.service.impl;

import com.lexor.admin.domain.entity.AdminAuditLog;
import com.lexor.admin.domain.entity.AdminDashboardSetting;
import com.lexor.admin.dto.request.RecordAuditLogRequest;
import com.lexor.admin.dto.request.UpdateSettingRequest;
import com.lexor.admin.dto.response.AdminAuditLogResponse;
import com.lexor.admin.dto.response.AdminSettingResponse;
import com.lexor.admin.dto.response.PageResponse;
import com.lexor.admin.repository.AdminAuditLogRepository;
import com.lexor.admin.repository.AdminDashboardSettingRepository;
import com.lexor.admin.security.UserPrincipal;
import com.lexor.admin.service.IAdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements IAdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;
    private final AdminDashboardSettingRepository settingRepository;

    @Override
    @Transactional
    public AdminAuditLogResponse recordAuditLog(UserPrincipal admin, RecordAuditLogRequest request, String ipAddress) {
        AdminAuditLog logEntry = AdminAuditLog.builder()
                .auditUuid(UUID.randomUUID().toString())
                .adminUserUuid(admin != null ? admin.getUserUuid() : "system")
                .action(request.getAction())
                .resourceType(request.getResourceType())
                .resourceUuid(request.getResourceUuid())
                .reason(request.getReason())
                .details(request.getDetails())
                .ipAddress(ipAddress)
                .status("SUCCESS")
                .build();

        AdminAuditLog saved = auditLogRepository.save(logEntry);
        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogResponse> getAuditLogs(
            String adminUserUuid,
            String action,
            String resourceType,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        Page<AdminAuditLog> page = auditLogRepository.searchAuditLogs(
                adminUserUuid, action, resourceType, startDate, endDate, pageable);

        List<AdminAuditLogResponse> content = page.getContent().stream()
                .map(this::mapToDto)
                .toList();

        return PageResponse.<AdminAuditLogResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminSettingResponse> getAllSettings() {
        return settingRepository.findAll().stream()
                .map(s -> AdminSettingResponse.builder()
                        .settingKey(s.getSettingKey())
                        .settingValue(s.getSettingValue())
                        .description(s.getDescription())
                        .updatedByUuid(s.getUpdatedByUuid())
                        .updatedAt(s.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public AdminSettingResponse updateSetting(UserPrincipal admin, UpdateSettingRequest request) {
        AdminDashboardSetting setting = settingRepository.findBySettingKey(request.getSettingKey())
                .orElse(AdminDashboardSetting.builder()
                        .settingKey(request.getSettingKey())
                        .description(request.getDescription())
                        .build());

        setting.setSettingValue(request.getSettingValue());
        if (request.getDescription() != null) {
            setting.setDescription(request.getDescription());
        }
        setting.setUpdatedByUuid(admin.getUserUuid());

        AdminDashboardSetting saved = settingRepository.save(setting);

        // Record audit
        recordAuditLog(admin, RecordAuditLogRequest.builder()
                .action("UPDATE_SETTING")
                .resourceType("DASHBOARD_SETTING")
                .resourceUuid(saved.getSettingKey())
                .reason("Administrator updated platform parameter: " + request.getSettingKey() + " = " + request.getSettingValue())
                .build(), null);

        return AdminSettingResponse.builder()
                .settingKey(saved.getSettingKey())
                .settingValue(saved.getSettingValue())
                .description(saved.getDescription())
                .updatedByUuid(saved.getUpdatedByUuid())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    private AdminAuditLogResponse mapToDto(AdminAuditLog entity) {
        return AdminAuditLogResponse.builder()
                .auditUuid(entity.getAuditUuid())
                .adminUserUuid(entity.getAdminUserUuid())
                .action(entity.getAction())
                .resourceType(entity.getResourceType())
                .resourceUuid(entity.getResourceUuid())
                .reason(entity.getReason())
                .details(entity.getDetails())
                .ipAddress(entity.getIpAddress())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
