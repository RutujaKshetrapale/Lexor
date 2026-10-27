package com.lexor.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lexor.gateway.config.LexorGatewayProperties;
import com.lexor.gateway.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class JwtAuthenticationGatewayFilter implements WebFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationGatewayFilter.class);

    private final LexorGatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationGatewayFilter(LexorGatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private SecretKey getSigningKey() {
        String secret = gatewayProperties.getJwt().getSecret();
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("Missing or insufficiently secure JWT secret. 'gateway.jwt.secret' must be at least 32 characters.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // 1. Always strip any incoming client-supplied spoofed identity headers
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(h -> {
                    h.remove("X-User-Uuid");
                    h.remove("X-User-Role");
                    h.remove("X-User-Email");
                    h.remove("X-User-Id");
                });

        // 2. Allow preflight OPTIONS requests without authentication
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 3. Bypass public endpoints
        if (isPublicEndpoint(path, method)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 4. Extract Authorization Header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Full authentication is required to access this resource");
        }

        String token = authHeader.substring(7).trim();
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            logger.warn("Expired JWT token attempt on {}: {}", path, ex.getMessage());
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "JWT token has expired");
        } catch (SignatureException | MalformedJwtException ex) {
            logger.warn("Invalid JWT token attempt on {}: {}", path, ex.getMessage());
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid or malformed JWT token");
        } catch (Exception ex) {
            logger.warn("JWT validation error on {}: {}", path, ex.getMessage());
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "JWT authentication failed: " + ex.getMessage());
        }

        // 5. Extract claims
        String userUuid = claims.getSubject();
        String roleClaim = claims.get("role", String.class);
        String email = claims.get("email", String.class);
        Object userIdObj = claims.get("userId");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : "";

        if (!StringUtils.hasText(userUuid)) {
            return sendErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token subject (missing UUID)");
        }

        String role = normalizeRole(roleClaim);

        // 6. Role-Based Access Control (RBAC) Enforcement
        if (!isAuthorizedForPath(path, role)) {
            logger.warn("Access denied: User {} with role {} attempted forbidden path {}", userUuid, role, path);
            return sendErrorResponse(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "You do not have permission to access this resource");
        }

        // 7. Mutate request downstream with verified headers
        requestBuilder.header("X-User-Uuid", userUuid);
        requestBuilder.header("X-User-Role", role);
        if (StringUtils.hasText(email)) {
            requestBuilder.header("X-User-Email", email);
        }
        if (StringUtils.hasText(userId)) {
            requestBuilder.header("X-User-Id", userId);
        }

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean isPublicEndpoint(String path, HttpMethod method) {
        if (path == null) {
            return false;
        }

        // Health and Actuator checks
        if (path.equals("/health") || path.startsWith("/actuator/")) {
            return true;
        }

        // Auth service endpoints
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }

        // Public Ride Type discovery (GET only)
        if (path.startsWith("/api/v1/ride-types") && HttpMethod.GET.equals(method) && !path.startsWith("/api/v1/admin/")) {
            return true;
        }

        // Payment provider webhook callbacks (authenticated via HMAC signature header, not JWT)
        if (path.startsWith("/api/v1/payments/webhooks/")) {
            return true;
        }

        // Public payment methods discovery
        if (path.equals("/api/v1/payments/methods") && HttpMethod.GET.equals(method)) {
            return true;
        }

        return false;
    }

    private String normalizeRole(String roleClaim) {
        if (!StringUtils.hasText(roleClaim)) {
            return "ROLE_RIDER";
        }
        String upper = roleClaim.trim().toUpperCase();
        if (upper.startsWith("ROLE_")) {
            return upper;
        }
        return "ROLE_" + upper;
    }

    private boolean isAuthorizedForPath(String path, String role) {
        if (path == null) return false;

        boolean isAdmin = "ROLE_ADMIN".equals(role);
        boolean isDriver = "ROLE_DRIVER".equals(role) || isAdmin;
        boolean isRider = "ROLE_RIDER".equals(role) || isAdmin;

        // Admin-only endpoints
        if (path.startsWith("/api/v1/admin/")) {
            return isAdmin;
        }

        // Driver-specific endpoints
        if (path.startsWith("/api/v1/drivers/me/bookings")) {
            return isDriver;
        }
        if (path.startsWith("/api/v1/drivers/")) {
            return isDriver;
        }
        if (path.startsWith("/api/v1/vehicles/")) {
            return isDriver;
        }

        // Rider endpoints
        if (path.startsWith("/api/v1/riders/")) {
            return isRider;
        }
        if (path.startsWith("/api/v1/ride-requests/")) {
            return isRider;
        }
        if (path.startsWith("/api/v1/bookings/")) {
            return isRider;
        }

        // General endpoints allow any authenticated role
        return true;
    }

    private Mono<Void> sendErrorResponse(ServerWebExchange exchange, HttpStatus status, String error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId != null) {
            response.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(exchange.getRequest().getURI().getPath())
                .correlationId(correlationId)
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
