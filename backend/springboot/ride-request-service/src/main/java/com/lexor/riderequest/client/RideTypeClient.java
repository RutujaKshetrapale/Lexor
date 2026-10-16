package com.lexor.riderequest.client;

import com.lexor.riderequest.client.dto.RideTypeDto;
import com.lexor.riderequest.exception.BadRequestException;
import com.lexor.riderequest.exception.ResourceNotFoundException;
import com.lexor.riderequest.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RideTypeClient {

    private static final Logger logger = LoggerFactory.getLogger(RideTypeClient.class);

    private final RestClient restClient;

    @Value("${services.ride-type-service.url:http://localhost:8089}")
    private String rideTypeServiceUrl;

    public Optional<RideTypeDto> getRideTypeByUuid(String rideTypeUuid, String bearerToken) {
        if (!StringUtils.hasText(rideTypeUuid)) {
            return Optional.empty();
        }

        String url = rideTypeServiceUrl + "/api/v1/ride-types/" + rideTypeUuid;
        logger.debug("Calling Ride Type Service at: {}", url);

        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(url);
            if (StringUtils.hasText(bearerToken)) {
                String token = bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;
                request.header("Authorization", token);
            }

            RideTypeDto dto = request.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        if (resp.getStatusCode().value() == 404) {
                            throw new ResourceNotFoundException("Ride category with UUID " + rideTypeUuid + " was not found or is currently disabled.");
                        }
                        throw new BadRequestException("Ride Type Service rejected query with status " + resp.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new ServiceUnavailableException("Ride Type Service returned internal error: " + resp.getStatusCode());
                    })
                    .body(RideTypeDto.class);

            if (dto != null && Boolean.FALSE.equals(dto.getIsActive())) {
                throw new BadRequestException("Ride category '" + dto.getName() + "' is currently disabled and unavailable for booking.");
            }

            return Optional.ofNullable(dto);

        } catch (ResourceNotFoundException | BadRequestException | ServiceUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            logger.warn("Ride Type Service at {} is unreachable or timed out: {}", url, ex.getMessage());
            throw new ServiceUnavailableException("Unable to contact Ride Type Service. Please verify service availability.");
        } catch (Exception ex) {
            logger.error("Unexpected error querying Ride Type Service at {}: {}", url, ex.getMessage());
            throw new ServiceUnavailableException("Unexpected error communicating with Ride Type Service: " + ex.getMessage());
        }
    }
}
