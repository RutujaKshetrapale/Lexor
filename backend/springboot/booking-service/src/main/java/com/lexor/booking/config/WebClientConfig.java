package com.lexor.booking.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final BookingProperties bookingProperties;

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofMillis(bookingProperties.getConnectTimeoutMs()).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofMillis(bookingProperties.getReadTimeoutMs()).toMillis());

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
