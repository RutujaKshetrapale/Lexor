package com.lexor.feedback.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RideEligibilityDto {

    private String rideUuid;
    private String bookingUuid;
    private String status;
    private String riderUuid;
    private String driverUuid;
    private LocalDateTime completedAt;
    private String riderName;
    private String driverName;

    public boolean isAuthoritativeCompleted() {
        if (status == null) return false;
        String s = status.trim().toUpperCase();
        return s.equals("COMPLETED") || s.equals("TRIP_COMPLETED")
                || s.equals("PAYMENT_COMPLETED") || s.equals("FEEDBACK");
    }
}
