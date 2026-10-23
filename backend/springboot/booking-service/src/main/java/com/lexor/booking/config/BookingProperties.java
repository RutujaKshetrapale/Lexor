package com.lexor.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "booking")
public class BookingProperties {

    private String matchingServiceUrl = "http://localhost:8093";
    private String rideRequestServiceUrl = "http://localhost:8091";
    private String driverServiceUrl = "http://localhost:8085";
    private String internalApiKey = "";
    private int otpExpirationMinutes = 120;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
}
