package com.lexor.matching.service;

import com.lexor.matching.dto.request.MatchingRetryRequest;
import com.lexor.matching.dto.response.MatchingAttemptResponse;

public interface DriverMatchingService {

    MatchingAttemptResponse initiateMatching(String requestUuid, String correlationId);

    MatchingAttemptResponse getMatchingStatus(String requestUuid);

    MatchingAttemptResponse retryMatching(String requestUuid, MatchingRetryRequest retryRequest);
}
