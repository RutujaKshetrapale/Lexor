package com.lexor.booking.client;

import com.lexor.booking.client.dto.RideRequestClientDto;
import com.lexor.booking.config.BookingProperties;
import com.lexor.booking.exception.BadRequestException;
import com.lexor.booking.exception.ForbiddenException;
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
public class RideRequestClient {

    private static final Logger logger = LoggerFactory.getLogger(RideRequestClient.class);

    private final RestClient restClient;
    private final BookingProperties bookingProperties;

    public Optional<RideRequestClientDto> getRideRequestByUuid(String requestUuid, String bearerToken) {
        if (!StringUtils.hasText(requestUuid)) {
            return Optional.empty();
        }

        String baseUrl = bookingProperties.getRideRequestServiceUrl().replaceAll("/+$", "");
        String url = baseUrl + "/api/v1/ride-requests/me/" + requestUuid.trim();
        logger.debug("Calling Ride Request Service at: {}", url);

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(url);
            if (StringUtils.hasText(bearerToken)) {
                String token = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;
                request.header("Authorization", token);
            }

            RideRequestClientDto dto = request.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int statusCode = resp.getStatusCode().value();
                        if (statusCode == 404) {
                            throw new ResourceNotFoundException("Ride request not found with UUID: " + requestUuid);
                        } else if (statusCode == 403) {
                            throw new ForbiddenException("Access denied: You do not own ride request " + requestUuid);
                        }
                        throw new BadRequestException("Ride Request Service rejected request with status: " + statusCode);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new ServiceUnavailableException("Ride Request Service returned internal error: " + resp.getStatusCode());
                    })
                    .body(RideRequestClientDto.class);

            return Optional.ofNullable(dto);
        } catch (ResourceNotFoundException | ForbiddenException | BadRequestException | ServiceUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            logger.error("Timeout or connection failure contacting Ride Request Service: {}", ex.getMessage());
            throw new ServiceUnavailableException("Ride Request Service is currently unreachable or timed out.");
        } catch (Exception ex) {
            logger.error("Unexpected error contacting Ride Request Service: {}", ex.getMessage());
            throw new ServiceUnavailableException("Failed to communicate with Ride Request Service: " + ex.getMessage());
        }
    }
}
