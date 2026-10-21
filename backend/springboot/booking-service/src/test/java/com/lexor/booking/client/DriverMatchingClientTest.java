package com.lexor.booking.client;

import com.lexor.booking.client.dto.DriverCandidateClientDto;
import com.lexor.booking.client.dto.MatchingAttemptClientDto;
import com.lexor.booking.client.dto.VehicleClientDto;
import com.lexor.booking.config.BookingProperties;
import com.lexor.booking.exception.BadRequestException;
import com.lexor.booking.exception.ConflictException;
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

class DriverMatchingClientTest {

    private MockRestServiceServer mockServer;
    private DriverMatchingClient driverMatchingClient;
    private BookingProperties bookingProperties;

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        bookingProperties.setMatchingServiceUrl("http://localhost:8093");
        bookingProperties.setInternalApiKey("test-internal-api-key");
        bookingProperties.setConnectTimeoutMs(3000);
        bookingProperties.setReadTimeoutMs(3000);

        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8093");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        driverMatchingClient = new DriverMatchingClient(restClient, bookingProperties);
    }

    @Test
    @DisplayName("Successfully queries matching status via GET with internal service key")
    void getMatchingStatus_Success() {
        String reqUuid = "req-uuid-111";
        String json = """
                {
                    "attemptUuid": "att-uuid-1",
                    "rideRequestUuid": "req-uuid-111",
                    "status": "MATCHED",
                    "attemptNumber": 1,
                    "distanceKm": 1.25,
                    "matchScore": 92.50,
                    "driver": {
                        "driverUuid": "driver-uuid-1",
                        "firstName": "Vikram",
                        "lastName": "Singh",
                        "phoneNumber": "+919876543212",
                        "rating": 4.95
                    },
                    "vehicle": {
                        "vehicleUuid": "veh-uuid-1",
                        "make": "Toyota",
                        "model": "Hyryder",
                        "licensePlate": "KA-01-EQ-4829",
                        "color": "Black"
                    }
                }
                """;

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", "test-internal-api-key"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<MatchingAttemptClientDto> result = driverMatchingClient.getMatchingStatus(reqUuid);

        assertTrue(result.isPresent());
        MatchingAttemptClientDto dto = result.get();
        assertEquals("MATCHED", dto.getStatus());
        assertEquals("att-uuid-1", dto.getAttemptUuid());
        assertNotNull(dto.getDriver());
        assertEquals("driver-uuid-1", dto.getDriver().getDriverUuid());
        assertNotNull(dto.getVehicle());
        assertEquals("KA-01-EQ-4829", dto.getVehicle().getLicensePlate());
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty or null requestUuid returns Optional.empty")
    void getMatchingStatus_EmptyUuid_ReturnsEmpty() {
        Optional<MatchingAttemptClientDto> result = driverMatchingClient.getMatchingStatus("   ");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("404 Not Found throws ResourceNotFoundException")
    void getMatchingStatus_NotFound_ThrowsResourceNotFound() {
        String reqUuid = "req-missing";

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", "test-internal-api-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () ->
                driverMatchingClient.getMatchingStatus(reqUuid));
        mockServer.verify();
    }

    @Test
    @DisplayName("409 Conflict throws ConflictException")
    void getMatchingStatus_Conflict_ThrowsConflict() {
        String reqUuid = "req-conflict";

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", "test-internal-api-key"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(ConflictException.class, () ->
                driverMatchingClient.getMatchingStatus(reqUuid));
        mockServer.verify();
    }

    @Test
    @DisplayName("500 Server Error throws ServiceUnavailableException")
    void getMatchingStatus_ServerError_ThrowsServiceUnavailable() {
        String reqUuid = "req-500";

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Service-Key", "test-internal-api-key"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceUnavailableException.class, () ->
                driverMatchingClient.getMatchingStatus(reqUuid));
        mockServer.verify();
    }

    @Test
    @DisplayName("Successfully initiates matching attempt via POST with correlation ID")
    void initiateMatching_Success() {
        String reqUuid = "req-uuid-222";
        String correlationId = "corr-12345";
        String json = """
                {
                    "attemptUuid": "att-uuid-2",
                    "rideRequestUuid": "req-uuid-222",
                    "status": "MATCHED",
                    "attemptNumber": 1,
                    "distanceKm": 2.10,
                    "matchScore": 88.00,
                    "driver": {
                        "driverUuid": "driver-uuid-2",
                        "firstName": "Anil",
                        "lastName": "Sharma",
                        "phoneNumber": "+919876543299",
                        "rating": 4.80
                    },
                    "vehicle": {
                        "vehicleUuid": "veh-uuid-2",
                        "make": "Hyundai",
                        "model": "Creta",
                        "licensePlate": "KA-02-AB-5555",
                        "color": "White"
                    }
                }
                """;

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid + "/attempts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Key", "test-internal-api-key"))
                .andExpect(header("X-Correlation-Id", "corr-12345"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<MatchingAttemptClientDto> result = driverMatchingClient.initiateMatching(reqUuid, correlationId);

        assertTrue(result.isPresent());
        MatchingAttemptClientDto dto = result.get();
        assertEquals("MATCHED", dto.getStatus());
        assertEquals("att-uuid-2", dto.getAttemptUuid());
        assertNotNull(dto.getDriver());
        assertEquals("driver-uuid-2", dto.getDriver().getDriverUuid());
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty or null requestUuid on initiate returns Optional.empty")
    void initiateMatching_EmptyUuid_ReturnsEmpty() {
        Optional<MatchingAttemptClientDto> result = driverMatchingClient.initiateMatching("", null);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("404 on initiate throws ResourceNotFoundException")
    void initiateMatching_NotFound_ThrowsResourceNotFound() {
        String reqUuid = "req-init-notfound";

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid + "/attempts"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () ->
                driverMatchingClient.initiateMatching(reqUuid, null));
        mockServer.verify();
    }

    @Test
    @DisplayName("409 Conflict on initiate throws ConflictException")
    void initiateMatching_Conflict_ThrowsConflict() {
        String reqUuid = "req-init-conflict";

        mockServer.expect(requestTo("http://localhost:8093/api/v1/internal/matching/ride-requests/" + reqUuid + "/attempts"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(ConflictException.class, () ->
                driverMatchingClient.initiateMatching(reqUuid, null));
        mockServer.verify();
    }
}
