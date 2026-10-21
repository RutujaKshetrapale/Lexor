package com.lexor.booking.client;

import com.lexor.booking.client.dto.RideRequestClientDto;
import com.lexor.booking.config.BookingProperties;
import com.lexor.booking.exception.BadRequestException;
import com.lexor.booking.exception.ForbiddenException;
import com.lexor.booking.exception.ResourceNotFoundException;
import com.lexor.booking.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RideRequestClientTest {

    private MockRestServiceServer mockServer;
    private RideRequestClient rideRequestClient;
    private BookingProperties bookingProperties;

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setRideRequestServiceUrl("http://localhost:8091");
        bookingProperties.setConnectTimeoutMs(3000);
        bookingProperties.setReadTimeoutMs(3000);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8091");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        rideRequestClient = new RideRequestClient(restClient, bookingProperties);
    }

    @Test
    @DisplayName("Successfully retrieves ride request by UUID with Bearer token")
    void getRideRequestByUuid_Success() {
        String reqUuid = "req-uuid-123";
        String json = """
                {
                    "requestUuid": "req-uuid-123",
                    "riderUuid": "rider-uuid-1",
                    "rideTypeCode": "GO",
                    "pickupAddress": "Indiranagar 100ft Rd",
                    "pickupLatitude": 12.9784,
                    "pickupLongitude": 77.6408,
                    "dropAddress": "Prestige Tech Park",
                    "dropLatitude": 12.9372,
                    "dropLongitude": 77.6949,
                    "estimatedDistanceKm": 11.5,
                    "estimatedDurationMinutes": 32,
                    "estimatedFare": 250.00,
                    "status": "REQUESTED"
                }
                """;

        mockServer.expect(requestTo("http://localhost:8091/api/v1/ride-requests/me/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-rider-jwt"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<RideRequestClientDto> result = rideRequestClient.getRideRequestByUuid(reqUuid, "test-rider-jwt");

        assertTrue(result.isPresent());
        RideRequestClientDto dto = result.get();
        assertEquals("req-uuid-123", dto.getRequestUuid());
        assertEquals("GO", dto.getRideTypeCode());
        assertEquals(new BigDecimal("250.00"), dto.getEstimatedFare());
        assertEquals("Indiranagar 100ft Rd", dto.getPickupAddress());
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty or null requestUuid returns Optional.empty")
    void getRideRequestByUuid_EmptyUuid_ReturnsEmpty() {
        Optional<RideRequestClientDto> result = rideRequestClient.getRideRequestByUuid("   ", "token");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("404 Not Found response throws ResourceNotFoundException")
    void getRideRequestByUuid_NotFound_ThrowsResourceNotFound() {
        String reqUuid = "req-missing";

        mockServer.expect(requestTo("http://localhost:8091/api/v1/ride-requests/me/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () ->
                rideRequestClient.getRideRequestByUuid(reqUuid, null));
        mockServer.verify();
    }

    @Test
    @DisplayName("403 Forbidden response throws ForbiddenException")
    void getRideRequestByUuid_Forbidden_ThrowsForbidden() {
        String reqUuid = "req-forbidden";

        mockServer.expect(requestTo("http://localhost:8091/api/v1/ride-requests/me/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(ForbiddenException.class, () ->
                rideRequestClient.getRideRequestByUuid(reqUuid, null));
        mockServer.verify();
    }

    @Test
    @DisplayName("500 Server Error response throws ServiceUnavailableException")
    void getRideRequestByUuid_ServerError_ThrowsServiceUnavailable() {
        String reqUuid = "req-server-err";

        mockServer.expect(requestTo("http://localhost:8091/api/v1/ride-requests/me/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceUnavailableException.class, () ->
                rideRequestClient.getRideRequestByUuid(reqUuid, null));
        mockServer.verify();
    }
}
