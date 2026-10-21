package com.lexor.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lexor.gateway.dto.ErrorResponse;
import com.lexor.gateway.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

@Component
public class GlobalGatewayExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GlobalGatewayExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalGatewayExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String error = "INTERNAL_SERVER_ERROR";
        String message = "An internal gateway error occurred.";

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value()) != null ?
                    HttpStatus.resolve(rse.getStatusCode().value()) : HttpStatus.INTERNAL_SERVER_ERROR;
            error = status.name();
            message = rse.getReason() != null ? rse.getReason() : ex.getMessage();
        } else if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            error = "GATEWAY_TIMEOUT";
            message = "Upstream microservice did not respond within the configured timeout window.";
            logger.warn("Gateway timeout forwarding to {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage());
        } else if (ex instanceof ConnectException || ex.getCause() instanceof ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            error = "SERVICE_UNAVAILABLE";
            message = "Target microservice is currently unreachable or starting up.";
            logger.warn("Connection failure forwarding to {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage());
        } else {
            logger.error("Unhandled gateway error on {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage(), ex);
        }

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
        } catch (JsonProcessingException jsonException) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // High priority error handler to intercept all exceptions before default Spring Boot error page
        return -2;
    }
}
