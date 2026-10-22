package com.lexor.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.jwt.secret=" + ApiGatewayIntegrationTest.TEST_SECRET,
        "gateway.rate-limit.burst-capacity=5",
        "gateway.rate-limit.replenish-rate=1"
})
public class ApiGatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    public static final String TEST_SECRET = "lexor-default-jwt-secret-key-32-chars-long!";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private String generateTestJwt(String userUuid, String role, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .issuer("LEXOR")
                .audience().add("LEXOR").and()
                .subject(userUuid)
                .claim("userId", 101L)
                .claim("email", "user@lexor.com")
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    @Test
    public void testHealthCheck_ReturnsUp() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("LEXOR Spring Cloud API Gateway");
    }

    @Test
    public void testCorrelationId_GeneratedWhenMissing() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .expectHeader().value("X-Correlation-Id", corrId -> {
                    assertThat(corrId).isNotBlank();
                });
    }

    @Test
    public void testCorrelationId_PreservedWhenSupplied() {
        String testCorrId = "corr-" + UUID.randomUUID();

        webTestClient.get()
                .uri("/health")
                .header("X-Correlation-Id", testCorrId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", testCorrId);
    }

    @Test
    public void testProtectedEndpoint_WithoutToken_Returns401Unauthorized() {
        webTestClient.get()
                .uri("/api/v1/bookings/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").value(msg -> assertThat(msg.toString()).contains("Full authentication is required"));
    }

    @Test
    public void testProtectedEndpoint_WithExpiredToken_Returns401Unauthorized() {
        String expiredToken = generateTestJwt("rider-uuid-1", "ROLE_RIDER", -10000L);

        webTestClient.get()
                .uri("/api/v1/bookings/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").value(msg -> assertThat(msg.toString()).contains("expired"));
    }

    @Test
    public void testProtectedEndpoint_WithInvalidSignatureToken_Returns401Unauthorized() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-for-testing-purposes-1234!".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
                .issuer("LEXOR")
                .subject("rider-uuid-1")
                .claim("role", "ROLE_RIDER")
                .signWith(wrongKey)
                .compact();

        webTestClient.get()
                .uri("/api/v1/bookings/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    @Test
    public void testRoleAuthorization_RiderAccessingAdminEndpoint_Returns403Forbidden() {
        String riderToken = generateTestJwt("rider-uuid-1", "ROLE_RIDER", 3600000L);

        webTestClient.get()
                .uri("/api/v1/admin/drivers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + riderToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN");
    }

    @Test
    public void testRoleAuthorization_RiderAccessingDriverEndpoint_Returns403Forbidden() {
        String riderToken = generateTestJwt("rider-uuid-1", "ROLE_RIDER", 3600000L);

        webTestClient.get()
                .uri("/api/v1/drivers/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + riderToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN");
    }

    @Test
    public void testRoleAuthorization_DriverAccessingRiderBookingEndpoint_Returns403Forbidden() {
        String driverToken = generateTestJwt("driver-uuid-1", "ROLE_DRIVER", 3600000L);

        webTestClient.get()
                .uri("/api/v1/bookings/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN");
    }

    @Test
    public void testInternalEndpoint_DirectAccessBlocked_Returns403Forbidden() {
        webTestClient.post()
                .uri("/api/v1/internal/matching/ride-requests/req-123/attempts")
                .header("X-Internal-Service-Key", "malicious-internal-key-attempt")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").value(msg -> assertThat(msg.toString()).contains("internal microservice endpoints"));
    }

    @Test
    public void testCorsPreflight_ReturnsAllowHeaders() {
        webTestClient.options()
                .uri("/api/v1/bookings/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    public void testRateLimiting_BurstExceeded_Returns429TooManyRequests() {
        String token = generateTestJwt("rate-limit-user-unique", "ROLE_RIDER", 3600000L);
        boolean got429 = false;
        for (int i = 0; i < 20; i++) {
            var result = webTestClient.get()
                    .uri("/api/v1/ride-types")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .exchange()
                    .returnResult(String.class);

            if (result.getStatus().value() == 429) {
                got429 = true;
                break;
            }
        }
        assertThat(got429).isTrue();
    }
}
