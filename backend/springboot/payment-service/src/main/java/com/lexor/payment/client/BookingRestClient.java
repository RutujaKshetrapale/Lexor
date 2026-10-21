package com.lexor.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.payment.client.dto.BookingDetailDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BookingRestClient implements IBookingClient {

    private static final Logger logger = LoggerFactory.getLogger(BookingRestClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.booking-url:http://localhost:8095}")
    private String bookingServiceUrl;

    @Override
    public Optional<BookingDetailDto> getBookingDetails(String bookingUuid, String authorizationHeader) {
        try {
            String url = bookingServiceUrl + "/api/v1/admin/bookings/" + bookingUuid;

            HttpHeaders headers = new HttpHeaders();
            if (authorizationHeader != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                BookingDetailDto dto = BookingDetailDto.builder()
                        .bookingUuid(root.path("bookingUuid").asText(bookingUuid))
                        .status(root.path("status").asText("CONFIRMED"))
                        .riderUuid(root.path("rider").path("riderUuid").asText())
                        .riderName(root.path("rider").path("firstName").asText("") + " " + root.path("rider").path("lastName").asText(""))
                        .driverUuid(root.path("driver").path("driverUuid").asText(null))
                        .driverName(root.path("driver").path("firstName").asText("") + " " + root.path("driver").path("lastName").asText(""))
                        .vehicleModel(root.path("vehicle").path("make").asText("") + " " + root.path("vehicle").path("model").asText(""))
                        .licensePlate(root.path("vehicle").path("licensePlate").asText(null))
                        .totalFare(root.path("totalFare").isNumber() ? BigDecimal.valueOf(root.path("totalFare").asDouble()) : BigDecimal.valueOf(250.00))
                        .currency(root.path("currency").asText("INR"))
                        .pickupAddress(root.path("pickupAddress").asText(""))
                        .dropAddress(root.path("dropAddress").asText(""))
                        .baseFare(BigDecimal.valueOf(50.00))
                        .distanceFare(BigDecimal.valueOf(150.00))
                        .timeFare(BigDecimal.valueOf(30.00))
                        .taxAmount(BigDecimal.valueOf(20.00))
                        .build();

                return Optional.of(dto);
            }
        } catch (Exception ex) {
            logger.warn("Could not fetch booking details from {}: {}", bookingServiceUrl, ex.getMessage());
        }
        return Optional.empty();
    }
}
