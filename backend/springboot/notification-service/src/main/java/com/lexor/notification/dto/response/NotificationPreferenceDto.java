package com.lexor.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDto {
    private String preferenceUuid;
    private String userUuid;
    private Boolean pushEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
    private Boolean rideUpdatesEnabled;
    private Boolean paymentUpdatesEnabled;
    private Boolean promotionalEnabled;
    private Boolean securityAlertsEnabled;
    private Boolean quietHoursEnabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private String preferredLocale;
}
