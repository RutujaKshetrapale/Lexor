package com.lexor.driver.dto.request;

import com.lexor.driver.entity.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAvailabilityRequest {

    private DriverStatus status;
    private Boolean isOnline;
}
