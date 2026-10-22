package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackAnalyticsResponse {

    private Long totalFeedbackCount;
    private Long visibleFeedbackCount;
    private Long underReviewFeedbackCount;
    private Long hiddenFeedbackCount;
    private Long removedFeedbackCount;
    private BigDecimal platformAverageRating;
    private Map<Integer, Long> ratingsDistribution;
    private List<TagCountDto> topTags;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TagCountDto {
        private String tagCode;
        private String label;
        private Long count;
    }
}
