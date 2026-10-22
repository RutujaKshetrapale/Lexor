package com.lexor.feedback.config;

import com.lexor.feedback.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public OpenAPI & Documentation
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Actuator Health & Metrics
                        .requestMatchers("/actuator/**").permitAll()

                        // Public Rating Summaries & Feedback Tags
                        .requestMatchers(HttpMethod.GET, "/api/v1/feedback/tags").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/drivers/*/rating-summary").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/riders/*/rating-summary").permitAll()

                        // Admin Only
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Driver Specific Endpoints
                        .requestMatchers("/api/v1/drivers/me/**").hasRole("DRIVER")

                        // Authenticated Feedback Operations
                        .requestMatchers("/api/v1/feedback/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
