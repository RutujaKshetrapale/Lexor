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
public class AdminSettingResponse {

    private String settingKey;
    private String settingValue;
    private String description;
    private String updatedByUuid;
    private LocalDateTime updatedAt;
}
