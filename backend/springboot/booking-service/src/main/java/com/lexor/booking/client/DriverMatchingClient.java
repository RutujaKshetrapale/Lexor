package com.lexor.booking.client;

import com.lexor.booking.client.dto.MatchingAttemptClientDto;
import com.lexor.booking.config.BookingProperties;
import com.lexor.booking.exception.BadRequestException;
import com.lexor.booking.exception.ConflictException;
import com.lexor.booking.exception.ResourceNotFoundException;
import com.lexor.booking.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DriverMatchingClient {

    private static final Logger logger = LoggerFactory.getLogger(DriverMatchingClient.class);

    private final RestClient restClient;
    private final BookingProperties bookingProperties;

    public Optional<MatchingAttemptClientDto> getMatchingStatus(String requestUuid) {
        if (!StringUtils.hasText(requestUuid)) {
            return Optional.empty();
        }

        String baseUrl = bookingProperties.getMatchingServiceUrl().replaceAll("/+$", "");
        String url = baseUrl + "/api/v1/internal/matching/ride-requests/" + requestUuid.trim();
        logger.debug("Calling Driver Matching Service status at: {}", url);

        try {
            MatchingAttemptClientDto dto = restClient.get()
                    .uri(url)
                    .header("X-Internal-Service-Key", bookingProperties.getInternalApiKey())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 404) {
                            throw new ResourceNotFoundException("No matching attempt found for ride request: " + requestUuid);
                        } else if (code == 409) {
                            throw new ConflictException("Matching status conflict for ride request: " + requestUuid);
                        }
                        throw new BadRequestException("Driver Matching Service rejected query with status: " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new ServiceUnavailableException("Driver Matching Service returned internal error: " + resp.getStatusCode());
                    })
                    .body(MatchingAttemptClientDto.class);

            return Optional.ofNullable(dto);
        } catch (ResourceNotFoundException | ConflictException | BadRequestException | ServiceUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            logger.error("Timeout or connection failure contacting Driver Matching Service: {}", ex.getMessage());
            throw new ServiceUnavailableException("Driver Matching Service is currently unreachable or timed out.");
        } catch (Exception ex) {
            logger.error("Unexpected error contacting Driver Matching Service: {}", ex.getMessage());
            throw new ServiceUnavailableException("Failed to communicate with Driver Matching Service: " + ex.getMessage());
        }
    }

    public Optional<MatchingAttemptClientDto> initiateMatching(String requestUuid, String correlationId) {
        if (!StringUtils.hasText(requestUuid)) {
            return Optional.empty();
        }

        String baseUrl = bookingProperties.getMatchingServiceUrl().replaceAll("/+$", "");
        String url = baseUrl + "/api/v1/internal/matching/ride-requests/" + requestUuid.trim() + "/attempts";
        logger.debug("Initiating matching via Driver Matching Service at: {}", url);

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(url)
                    .header("X-Internal-Service-Key", bookingProperties.getInternalApiKey());

            if (StringUtils.hasText(correlationId)) {
                spec.header("X-Correlation-Id", correlationId);
            }

            MatchingAttemptClientDto dto = spec.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 404) {
                            throw new ResourceNotFoundException("Ride request not found for matching: " + requestUuid);
                        } else if (code == 409) {
                            throw new ConflictException("Ride request cannot be matched in current state: " + requestUuid);
                        }
                        throw new BadRequestException("Driver Matching Service rejected initiation with status: " + code);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new ServiceUnavailableException("Driver Matching Service returned internal error: " + resp.getStatusCode());
                    })
                    .body(MatchingAttemptClientDto.class);

            return Optional.ofNullable(dto);
        } catch (ResourceNotFoundException | ConflictException | BadRequestException | ServiceUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            logger.error("Timeout or connection failure initiating matching: {}", ex.getMessage());
            throw new ServiceUnavailableException("Driver Matching Service is currently unreachable or timed out.");
        } catch (Exception ex) {
            logger.error("Unexpected error initiating matching: {}", ex.getMessage());
            throw new ServiceUnavailableException("Failed to communicate with Driver Matching Service: " + ex.getMessage());
        }
    }
}
