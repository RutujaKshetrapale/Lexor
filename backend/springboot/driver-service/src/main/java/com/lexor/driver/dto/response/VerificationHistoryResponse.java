package com.lexor.driver.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationHistoryResponse {

    private Long id;
    private Long driverId;
    private Long documentId;
    private String actionType;
    private String previousStatus;
    private String newStatus;
    private Long reviewedByAdminId;
    private String reviewedByAdminName;
    private String reason;
    private LocalDateTime createdAt;
}
