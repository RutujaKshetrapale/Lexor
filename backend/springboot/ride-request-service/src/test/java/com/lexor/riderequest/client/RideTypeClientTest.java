package com.lexor.riderequest.client;

import com.lexor.riderequest.client.dto.RideTypeDto;
import com.lexor.riderequest.exception.BadRequestException;
import com.lexor.riderequest.exception.ResourceNotFoundException;
import com.lexor.riderequest.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
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

class RideTypeClientTest {

    private MockRestServiceServer mockServer;
    private RideTypeClient rideTypeClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8089");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        rideTypeClient = new RideTypeClient(restClient);
        ReflectionTestUtils.setField(rideTypeClient, "rideTypeServiceUrl", "http://localhost:8089");
    }

    @Test
    @DisplayName("Successfully retrieves active ride type with Bearer authorization")
    void getRideTypeByUuid_Success() {
        String uuid = "rt-uuid-123";
        String json = """
                {
                    "uuid": "rt-uuid-123",
                    "code": "GO",
                    "name": "LEXOR Go",
                    "baseFare": 50.00,
                    "perKmRate": 15.00,
                    "perMinuteRate": 2.00,
                    "isActive": true
                }
                """;

        mockServer.expect(requestTo("http://localhost:8089/api/v1/ride-types/" + uuid))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-jwt-token"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<RideTypeDto> result = rideTypeClient.getRideTypeByUuid(uuid, "Bearer test-jwt-token");

        assertTrue(result.isPresent());
        assertEquals("GO", result.get().getCode());
        assertEquals("LEXOR Go", result.get().getName());
        assertEquals(new BigDecimal("50.00"), result.get().getBaseFare());
        assertTrue(result.get().getIsActive());
        mockServer.verify();
    }

    @Test
    @DisplayName("Empty or null ride type UUID returns Optional.empty")
    void getRideTypeByUuid_EmptyUuid_ReturnsEmpty() {
        Optional<RideTypeDto> result = rideTypeClient.getRideTypeByUuid("   ", "token");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Disabled ride category throws BadRequestException")
    void getRideTypeByUuid_DisabledCategory_ThrowsBadRequest() {
        String uuid = "rt-uuid-disabled";
        String json = """
                {
                    "uuid": "rt-uuid-disabled",
                    "code": "VINTAGE",
                    "name": "LEXOR Vintage",
                    "baseFare": 200.00,
                    "perKmRate": 30.00,
                    "perMinuteRate": 5.00,
                    "isActive": false
                }
                """;

        mockServer.expect(requestTo("http://localhost:8089/api/v1/ride-types/" + uuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThrows(BadRequestException.class, () ->
                rideTypeClient.getRideTypeByUuid(uuid, null));
        mockServer.verify();
    }

    @Test
    @DisplayName("404 Not Found response throws ResourceNotFoundException")
    void getRideTypeByUuid_NotFound_ThrowsResourceNotFound() {
        String uuid = "rt-uuid-missing";

        mockServer.expect(requestTo("http://localhost:8089/api/v1/ride-types/" + uuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () ->
                rideTypeClient.getRideTypeByUuid(uuid, null));
        mockServer.verify();
    }

    @Test
    @DisplayName("500 Server Error response throws ServiceUnavailableException")
    void getRideTypeByUuid_ServerError_ThrowsServiceUnavailable() {
        String uuid = "rt-uuid-err";

        mockServer.expect(requestTo("http://localhost:8089/api/v1/ride-types/" + uuid))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceUnavailableException.class, () ->
                rideTypeClient.getRideTypeByUuid(uuid, null));
        mockServer.verify();
    }
}
