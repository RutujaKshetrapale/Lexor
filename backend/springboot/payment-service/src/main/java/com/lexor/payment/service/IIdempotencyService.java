package com.lexor.payment.service;

import java.util.Optional;

public interface IIdempotencyService {

    <T> Optional<T> findCachedResponse(String callerUuid, String idempotencyKey, Class<T> responseClass);

    void storeResponse(String callerUuid, String idempotencyKey, String resourceType, int statusCode, Object payload);
}
