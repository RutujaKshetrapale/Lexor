package com.lexor.feedback.service;

import com.lexor.feedback.dto.response.DriverRatingSummaryResponse;
import com.lexor.feedback.dto.response.RiderRatingSummaryResponse;
import com.lexor.feedback.entity.RideFeedback;
import com.lexor.feedback.enums.FeedbackRole;

public interface IRatingCalculationService {

    void updateRatingSummaryOnNewFeedback(RideFeedback feedback);

    void updateRatingSummaryOnRatingEdit(RideFeedback feedback, int oldRating);

    void recalculateSummaryForTarget(String targetUuid, FeedbackRole targetRole);

    DriverRatingSummaryResponse getDriverSummary(String driverUuid);

    RiderRatingSummaryResponse getRiderSummary(String riderUuid);
}
