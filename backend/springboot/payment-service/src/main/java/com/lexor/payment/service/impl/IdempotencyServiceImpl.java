package com.lexor.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.payment.entity.PaymentIdempotencyEntity;
import com.lexor.payment.repository.PaymentIdempotencyRepository;
import com.lexor.payment.service.IIdempotencyService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IIdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyServiceImpl.class);

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public <T> Optional<T> findCachedResponse(String callerUuid, String idempotencyKey, Class<T> responseClass) {
        if (callerUuid == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        Optional<PaymentIdempotencyEntity> recordOpt = idempotencyRepository
                .findByCallerUuidAndIdempotencyKey(callerUuid, idempotencyKey);

        if (recordOpt.isPresent()) {
            PaymentIdempotencyEntity record = recordOpt.get();
            if (record.getExpiresAt().isAfter(LocalDateTime.now())) {
                try {
                    T response = objectMapper.readValue(record.getResponsePayload(), responseClass);
                    logger.info("Found cached idempotent response for caller {} and key {}", callerUuid, idempotencyKey);
                    return Optional.of(response);
                } catch (Exception ex) {
                    logger.error("Failed to deserialize cached idempotent response: {}", ex.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void storeResponse(String callerUuid, String idempotencyKey, String resourceType, int statusCode, Object payload) {
        if (callerUuid == null || idempotencyKey == null || idempotencyKey.isBlank() || payload == null) {
            return;
        }

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            PaymentIdempotencyEntity entity = PaymentIdempotencyEntity.builder()
                    .callerUuid(callerUuid)
                    .idempotencyKey(idempotencyKey)
                    .resourceType(resourceType)
                    .responseStatusCode(statusCode)
                    .responsePayload(jsonPayload)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .build();

            idempotencyRepository.save(entity);
            logger.info("Stored idempotent response for caller {} and key {}", callerUuid, idempotencyKey);
        } catch (Exception ex) {
            logger.warn("Could not save idempotency record: {}", ex.getMessage());
        }
    }
}
