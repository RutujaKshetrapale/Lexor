package com.lexor.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "matching")
public class MatchingProperties {

    /**
     * Maximum geographical search radius for driver candidates in kilometers.
     */
    private double maxRadiusKm = 10.0;

    /**
     * Maximum permissible age for driver GPS coordinates in seconds before considered stale.
     */
    private long maxLocationAgeSeconds = 300;

    /**
     * Time-to-live duration for driver reservations in seconds.
     */
    private long reservationTtlSeconds = 120;

    /**
     * Shared internal service key for protected inter-service RPC calls.
     */
    private String internalApiKey = "lexor_internal_matching_service_key_2026";
}
