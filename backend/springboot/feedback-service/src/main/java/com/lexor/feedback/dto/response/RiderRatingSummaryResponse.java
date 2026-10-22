package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.feedback.entity.RiderRatingSummary;
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
public class RiderRatingSummaryResponse {

    private String riderUuid;
    private BigDecimal averageRating;
    private Integer totalRatings;
    private Map<Integer, Integer> ratingDistribution;
    private Integer excludedRatingsCount;
    private Integer totalCompletedRides;
    private LocalDateTime lastRecalculatedAt;

    public static RiderRatingSummaryResponse fromEntity(RiderRatingSummary summary) {
        if (summary == null) return null;

        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        distribution.put(5, summary.getFiveStarCount());
        distribution.put(4, summary.getFourStarCount());
        distribution.put(3, summary.getThreeStarCount());
        distribution.put(2, summary.getTwoStarCount());
        distribution.put(1, summary.getOneStarCount());

        return RiderRatingSummaryResponse.builder()
                .riderUuid(summary.getRiderUuid())
                .averageRating(summary.getAverageRating())
                .totalRatings(summary.getTotalRatings())
                .ratingDistribution(distribution)
                .excludedRatingsCount(summary.getExcludedRatingsCount())
                .totalCompletedRides(summary.getTotalCompletedRides())
                .lastRecalculatedAt(summary.getLastRecalculatedAt())
                .build();
    }
}
