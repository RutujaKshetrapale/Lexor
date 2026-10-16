package com.lexor.riderequest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${services.ride-type-service.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${services.ride-type-service.read-timeout-ms:3000}")
    private int readTimeoutMs;

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
