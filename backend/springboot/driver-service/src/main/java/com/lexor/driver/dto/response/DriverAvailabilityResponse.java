package com.lexor.driver.dto.response;

import com.lexor.driver.entity.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAvailabilityResponse {

    private String driverUuid;
    private DriverStatus status;
    private boolean isOnline;
    private String message;
}
