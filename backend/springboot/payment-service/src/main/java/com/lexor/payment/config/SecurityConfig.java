package com.lexor.payment.config;

import com.lexor.payment.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public Health & Diagnostics
                        .requestMatchers("/health", "/health/**", "/actuator/**").permitAll()
                        // OpenAPI Swagger Documentation
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Provider Webhook Callback (Authenticates via provider signature, not JWT)
                        .requestMatchers("/api/v1/payments/webhooks/**").permitAll()
                        // Payment methods list (public or rider)
                        .requestMatchers("/api/v1/payments/methods").permitAll()
                        // Driver cash collection
                        .requestMatchers("/api/v1/driver/payments/**").hasRole("DRIVER")
                        // Admin endpoints
                        .requestMatchers("/api/v1/admin/payments/**").hasRole("ADMIN")
                        // Rider and general payments
                        .requestMatchers("/api/v1/payments/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
