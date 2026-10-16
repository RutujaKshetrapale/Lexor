package com.lexor.riderequest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexor.riderequest.client.RideTypeClient;
import com.lexor.riderequest.client.dto.RideTypeDto;
import com.lexor.riderequest.dto.request.CancelRideRequest;
import com.lexor.riderequest.dto.request.CreateRideRequest;
import com.lexor.riderequest.entity.RideRequest;
import com.lexor.riderequest.entity.RideRequestStatus;
import com.lexor.riderequest.entity.RideType;
import com.lexor.riderequest.entity.RiderProfile;
import com.lexor.riderequest.entity.Role;
import com.lexor.riderequest.entity.User;
import com.lexor.riderequest.entity.UserStatus;
import com.lexor.riderequest.repository.RideRequestRepository;
import com.lexor.riderequest.repository.RideTypeRepository;
import com.lexor.riderequest.repository.RiderProfileRepository;
import com.lexor.riderequest.repository.UserRepository;
import com.lexor.riderequest.security.JwtTokenProvider;
import com.lexor.riderequest.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RideRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderProfileRepository riderProfileRepository;

    @Autowired
    private RideTypeRepository rideTypeRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @MockBean
    private RideTypeClient rideTypeClient;

    private User rider1User;
    private User rider2User;
    private User driverUser;

    private RiderProfile rider1Profile;
    private RiderProfile rider2Profile;

    private RideType goRideType;
    private RideType disabledRideType;

    private String rider1Token;
    private String rider2Token;
    private String driverToken;

    @BeforeEach
    void setUp() {
        rideRequestRepository.deleteAll();
        riderProfileRepository.deleteAll();
        rideTypeRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create Rider 1
        rider1User = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Rajesh")
                .lastName("Kumar")
                .email("rajesh.kumar@example.com")
                .phoneNumber("+919876543211")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        rider1Profile = riderProfileRepository.save(RiderProfile.builder()
                .user(rider1User)
                .rating(new BigDecimal("4.85"))
                .totalRides(12)
                .preferredPaymentMethod("UPI")
                .build());

        // 2. Create Rider 2
        rider2User = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Ananya")
                .lastName("Sharma")
                .email("ananya.sharma@example.com")
                .phoneNumber("+919876543222")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.RIDER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        rider2Profile = riderProfileRepository.save(RiderProfile.builder()
                .user(rider2User)
                .rating(new BigDecimal("4.90"))
                .totalRides(5)
                .preferredPaymentMethod("CARD")
                .build());

        // 3. Create Driver
        driverUser = userRepository.save(User.builder()
                .uuid(UUID.randomUUID().toString())
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@example.com")
                .phoneNumber("+919876543212")
                .passwordHash("$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFj5/5l.K1N6r7F0WpE0yP1O2Q3R4S5T")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        // 4. Create Active and Disabled Ride Types
        goRideType = rideTypeRepository.save(RideType.builder()
                .name("LEXOR Go")
                .code("GO")
                .description("Affordable hatchback rides")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .isActive(true)
                .build());

        disabledRideType = rideTypeRepository.save(RideType.builder()
                .name("LEXOR Helicopter")
                .code("HELI")
                .description("Aerial mobility")
                .baseFare(new BigDecimal("5000.00"))
                .perKmRate(new BigDecimal("500.00"))
                .perMinuteRate(new BigDecimal("100.00"))
                .minimumFare(new BigDecimal("10000.00"))
                .cancellationFee(new BigDecimal("2000.00"))
                .capacity(2)
                .isActive(false)
                .build());

        // 5. Generate JWT tokens
        rider1Token = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(rider1User));
        rider2Token = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(rider2User));
        driverToken = "Bearer " + jwtTokenProvider.generateTokenFromUserPrincipal(UserPrincipal.create(driverUser));
    }

    @Test
    @DisplayName("1. Successful creation of ride request by authenticated rider")
    void testCreateRideRequestSuccess() throws Exception {
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Indiranagar, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Marathahalli, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("GO")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestUuid", notNullValue()))
                .andExpect(jsonPath("$.riderUuid", is(rider1User.getUuid())))
                .andExpect(jsonPath("$.riderName", is("Rajesh Kumar")))
                .andExpect(jsonPath("$.rideTypeCode", is("GO")))
                .andExpect(jsonPath("$.rideTypeName", is("LEXOR Go")))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.status", is("REQUESTED")))
                .andExpect(jsonPath("$.estimatedDistanceKm", notNullValue()))
                .andExpect(jsonPath("$.estimatedDurationMinutes", notNullValue()))
                .andExpect(jsonPath("$.estimatedFare", notNullValue()));
    }

    @Test
    @DisplayName("2. Reject request when JWT token is missing (401)")
    void testCreateRideRequestMissingToken() throws Exception {
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Indiranagar, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Marathahalli, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("GO")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("3. Reject request when JWT token is invalid (401)")
    void testCreateRideRequestInvalidToken() throws Exception {
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("GO")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", "Bearer invalid.malformed.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("4. Driver attempting rider-only operation is forbidden (403)")
    void testDriverCannotCreateRideRequest() throws Exception {
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("GO")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")));
    }

    @Test
    @DisplayName("5. Reject missing and blank input fields (400)")
    void testCreateRideRequestValidationErrors() throws Exception {
        CreateRideRequest invalidRequest = CreateRideRequest.builder()
                .pickupAddress("") // Blank
                .pickupLatitude(null) // Missing
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("A") // Too short
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(null) // Missing
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.details.pickupAddress", notNullValue()))
                .andExpect(jsonPath("$.details.pickupLatitude", notNullValue()))
                .andExpect(jsonPath("$.details.dropAddress", notNullValue()))
                .andExpect(jsonPath("$.details.dropLongitude", notNullValue()));
    }

    @Test
    @DisplayName("6. Reject invalid coordinate ranges and identical coordinates (400)")
    void testRejectInvalidCoordinates() throws Exception {
        // Test Latitude > 90.0
        CreateRideRequest invalidLat = CreateRideRequest.builder()
                .pickupAddress("Valid Pickup Address")
                .pickupLatitude(new BigDecimal("95.00000000")) // Invalid: > 90
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Valid Drop Address")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("GO")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLat)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));

        // Test Identical Coordinates
        CreateRideRequest identicalCoords = CreateRideRequest.builder()
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("MG Road, Bengaluru")
                .dropLatitude(new BigDecimal("12.97840000"))
                .dropLongitude(new BigDecimal("77.64080000"))
                .rideTypeCode("GO")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(identicalCoords)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("7. Reject disabled ride category (400)")
    void testRejectDisabledRideCategory() throws Exception {
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("HELI") // Disabled
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("8. Reject unknown ride category (404)")
    void testRejectUnknownRideCategory() throws Exception {
        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeCode("NON_EXISTENT_CATEGORY")
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("9. Resolve ride type through Ride Type Service via UUID")
    void testCreateRideRequestWithRideTypeUuidClientIntegration() throws Exception {
        String testUuid = "a1b2c3d4-0001-4000-8000-000000000001";
        RideTypeDto dto = RideTypeDto.builder()
                .uuid(testUuid)
                .name("LEXOR Go")
                .code("GO")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .isActive(true)
                .build();

        when(rideTypeClient.getRideTypeByUuid(eq(testUuid), anyString())).thenReturn(Optional.of(dto));

        CreateRideRequest request = CreateRideRequest.builder()
                .pickupAddress("MG Road, Indiranagar, Bengaluru")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Prestige Tech Park, Marathahalli, Bengaluru")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .rideTypeUuid(testUuid)
                .build();

        mockMvc.perform(post("/api/v1/ride-requests")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rideTypeCode", is("GO")))
                .andExpect(jsonPath("$.status", is("REQUESTED")));
    }

    @Test
    @DisplayName("10. Rider cannot access another rider's request by UUID (403 Forbidden)")
    void testRiderCannotReadAnotherRidersRequest() throws Exception {
        // Create request owned by Rider 1
        RideRequest rider1Request = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Rider 1 Pickup")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Rider 1 Drop")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("10.00"))
                .estimatedDurationMinutes(25)
                .estimatedFare(new BigDecimal("200.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build());

        // Rider 2 attempts to read Rider 1's request
        mockMvc.perform(get("/api/v1/ride-requests/me/" + rider1Request.getUuid())
                        .header("Authorization", rider2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")))
                .andExpect(jsonPath("$.message", is("You do not have permission to view this ride request")));
    }

    @Test
    @DisplayName("11. Rider cannot cancel another rider's request (403 Forbidden)")
    void testRiderCannotCancelAnotherRidersRequest() throws Exception {
        // Create request owned by Rider 1
        RideRequest rider1Request = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Rider 1 Pickup")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Rider 1 Drop")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("10.00"))
                .estimatedDurationMinutes(25)
                .estimatedFare(new BigDecimal("200.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build());

        CancelRideRequest cancelRequest = CancelRideRequest.builder().reason("Malicious cancel attempt").build();

        // Rider 2 attempts to cancel Rider 1's request
        mockMvc.perform(patch("/api/v1/ride-requests/" + rider1Request.getUuid() + "/cancel")
                        .header("Authorization", rider2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")))
                .andExpect(jsonPath("$.message", is("You do not have permission to cancel this ride request")));
    }

    @Test
    @DisplayName("12. Owner successfully cancels own request when status is REQUESTED (200)")
    void testOwnerCancelsOwnRequestSuccess() throws Exception {
        RideRequest request = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Rider 1 Pickup")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Rider 1 Drop")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("10.00"))
                .estimatedDurationMinutes(25)
                .estimatedFare(new BigDecimal("200.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build());

        CancelRideRequest cancelBody = CancelRideRequest.builder().reason("Meeting got rescheduled").build();

        mockMvc.perform(patch("/api/v1/ride-requests/" + request.getUuid() + "/cancel")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestUuid", is(request.getUuid())))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @DisplayName("13. Reject repeated cancellation attempt on already cancelled request (409 Conflict)")
    void testRepeatedCancellationRejected() throws Exception {
        RideRequest request = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Rider 1 Pickup")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Rider 1 Drop")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("10.00"))
                .estimatedDurationMinutes(25)
                .estimatedFare(new BigDecimal("200.00"))
                .status(RideRequestStatus.CANCELLED)
                .requestedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(patch("/api/v1/ride-requests/" + request.getUuid() + "/cancel")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")))
                .andExpect(jsonPath("$.message", is("Ride request is already cancelled.")));
    }

    @Test
    @DisplayName("14. Reject cancellation when request is already MATCHED (409 Conflict)")
    void testCancelMatchedRequestRejected() throws Exception {
        RideRequest request = rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Rider 1 Pickup")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Rider 1 Drop")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("10.00"))
                .estimatedDurationMinutes(25)
                .estimatedFare(new BigDecimal("200.00"))
                .status(RideRequestStatus.MATCHED)
                .requestedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(patch("/api/v1/ride-requests/" + request.getUuid() + "/cancel")
                        .header("Authorization", rider1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")));
    }

    @Test
    @DisplayName("15. Paginated listing of rider's own requests with status filtering")
    void testGetMyRideRequestsPaginationAndFiltering() throws Exception {
        // Save 2 REQUESTED and 1 CANCELLED for Rider 1
        rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Trip 1")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Drop 1")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("10.00"))
                .estimatedDurationMinutes(25)
                .estimatedFare(new BigDecimal("200.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now().minusHours(2))
                .build());

        rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Trip 2")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Drop 2")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("12.00"))
                .estimatedDurationMinutes(30)
                .estimatedFare(new BigDecimal("230.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now().minusHours(1))
                .build());

        rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider1Profile)
                .rideType(goRideType)
                .pickupAddress("Trip 3 (Cancelled)")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Drop 3")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("5.00"))
                .estimatedDurationMinutes(15)
                .estimatedFare(new BigDecimal("110.00"))
                .status(RideRequestStatus.CANCELLED)
                .requestedAt(LocalDateTime.now())
                .build());

        // Also save 1 request for Rider 2 (should never appear in Rider 1's listing)
        rideRequestRepository.save(RideRequest.builder()
                .uuid(UUID.randomUUID().toString())
                .rider(rider2Profile)
                .rideType(goRideType)
                .pickupAddress("Rider 2 Trip")
                .pickupLatitude(new BigDecimal("12.97840000"))
                .pickupLongitude(new BigDecimal("77.64080000"))
                .dropAddress("Rider 2 Drop")
                .dropLatitude(new BigDecimal("12.93720000"))
                .dropLongitude(new BigDecimal("77.69490000"))
                .estimatedDistanceKm(new BigDecimal("7.00"))
                .estimatedDurationMinutes(18)
                .estimatedFare(new BigDecimal("140.00"))
                .status(RideRequestStatus.REQUESTED)
                .requestedAt(LocalDateTime.now())
                .build());

        // 1. Fetch all for Rider 1 -> Should return 3 elements
        mockMvc.perform(get("/api/v1/ride-requests/me?page=0&size=10")
                        .header("Authorization", rider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.content", hasSize(3)));

        // 2. Fetch with filter status=REQUESTED -> Should return 2 elements
        mockMvc.perform(get("/api/v1/ride-requests/me?status=REQUESTED")
                        .header("Authorization", rider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }
}
