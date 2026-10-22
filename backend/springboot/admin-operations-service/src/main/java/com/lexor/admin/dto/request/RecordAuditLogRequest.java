package com.lexor.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordAuditLogRequest {

    @NotBlank(message = "Action is required")
    private String action;

    @NotBlank(message = "Resource type is required")
    private String resourceType;

    private String resourceUuid;

    private String reason;

    private String details;
}
