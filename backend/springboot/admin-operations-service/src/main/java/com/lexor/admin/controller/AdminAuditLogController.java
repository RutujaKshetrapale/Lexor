package com.lexor.admin.controller;

import com.lexor.admin.dto.request.RecordAuditLogRequest;
import com.lexor.admin.dto.request.UpdateSettingRequest;
import com.lexor.admin.dto.response.AdminAuditLogResponse;
import com.lexor.admin.dto.response.AdminSettingResponse;
import com.lexor.admin.dto.response.ApiResponse;
import com.lexor.admin.dto.response.PageResponse;
import com.lexor.admin.security.UserPrincipal;
import com.lexor.admin.service.IAdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Audit Logs & Settings", description = "Endpoints for immutable administrative activity trails and dynamic dashboard settings")
@SecurityRequirement(name = "BearerAuthentication")
public class AdminAuditLogController {

    private final IAdminAuditService auditService;

    @Operation(summary = "Search and list administrative audit logs", description = "Query immutable records of all administrative actions with multi-dimensional filtering")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminAuditLogResponse>>> listAuditLogs(
            @RequestParam(required = false) String adminUserUuid,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<AdminAuditLogResponse> result = auditService.getAuditLogs(
                adminUserUuid, action, resourceType, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Record an administrative audit event", description = "Manually record an action event executed by a platform administrator")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminAuditLogResponse>> recordAudit(
            @AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody RecordAuditLogRequest request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getRemoteAddr();
        AdminAuditLogResponse result = auditService.recordAuditLog(admin, request, ip);
        return ResponseEntity.ok(ApiResponse.success(result, "Audit log recorded successfully"));
    }

    @Operation(summary = "List all platform dashboard settings", description = "Inspect dynamic platform operational settings and tuning thresholds")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<List<AdminSettingResponse>>> listSettings() {
        List<AdminSettingResponse> result = auditService.getAllSettings();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Update a platform dashboard setting", description = "Update operational setting value with automated audit trail logging")
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<AdminSettingResponse>> updateSetting(
            @AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody UpdateSettingRequest request
    ) {
        AdminSettingResponse result = auditService.updateSetting(admin, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Setting updated successfully"));
    }
}
