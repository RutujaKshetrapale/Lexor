package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.feedback.entity.DriverRatingSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DriverRatingSummaryResponse {

    private String driverUuid;
    private BigDecimal averageRating;
    private Integer totalRatings;
    private Map<Integer, Integer> ratingDistribution;
    private Integer excludedRatingsCount;
    private Integer totalCompletedRides;
    private String recentTrend;
    private LocalDateTime lastRecalculatedAt;

    public static DriverRatingSummaryResponse fromEntity(DriverRatingSummary summary) {
        if (summary == null) return null;

        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        distribution.put(5, summary.getFiveStarCount());
        distribution.put(4, summary.getFourStarCount());
        distribution.put(3, summary.getThreeStarCount());
        distribution.put(2, summary.getTwoStarCount());
        distribution.put(1, summary.getOneStarCount());

        String trend = "NEW";
        if (summary.getTotalRatings() > 0) {
            double avg = summary.getAverageRating().doubleValue();
            if (avg >= 4.8) trend = "EXCELLENT";
            else if (avg >= 4.5) trend = "GOOD";
            else if (avg >= 4.0) trend = "ACCEPTABLE";
            else trend = "ATTENTION_NEEDED";
        }

        return DriverRatingSummaryResponse.builder()
                .driverUuid(summary.getDriverUuid())
                .averageRating(summary.getAverageRating())
                .totalRatings(summary.getTotalRatings())
                .ratingDistribution(distribution)
                .excludedRatingsCount(summary.getExcludedRatingsCount())
                .totalCompletedRides(summary.getTotalCompletedRides())
                .recentTrend(trend)
                .lastRecalculatedAt(summary.getLastRecalculatedAt())
                .build();
    }
}
