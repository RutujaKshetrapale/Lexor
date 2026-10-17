package com.lexor.matching;

import com.lexor.matching.config.MatchingProperties;
import com.lexor.matching.dto.request.LocationUpdateRequest;
import com.lexor.matching.dto.response.LocationUpdateResponse;
import com.lexor.matching.entity.DriverLocation;
import com.lexor.matching.entity.DriverProfile;
import com.lexor.matching.entity.Role;
import com.lexor.matching.entity.User;
import com.lexor.matching.entity.UserStatus;
import com.lexor.matching.exception.BadRequestException;
import com.lexor.matching.exception.ResourceNotFoundException;
import com.lexor.matching.repository.DriverLocationRepository;
import com.lexor.matching.repository.DriverProfileRepository;
import com.lexor.matching.security.UserPrincipal;
import com.lexor.matching.service.impl.DriverLocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverLocationServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private DriverLocationRepository driverLocationRepository;

    @Mock
    private MatchingProperties matchingProperties;

    @InjectMocks
    private DriverLocationServiceImpl driverLocationService;

    private UserPrincipal driverPrincipal;
    private DriverProfile driverProfile;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(3L)
                .uuid("33333333-3333-4333-8333-333333333333")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram.singh@example.com")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        driverProfile = DriverProfile.builder()
                .id(1L)
                .user(user)
                .build();

        driverPrincipal = new UserPrincipal(
                3L,
                "33333333-3333-4333-8333-333333333333",
                "Vikram",
                "Singh",
                "vikram.singh@example.com",
                "hash",
                Role.DRIVER,
                UserStatus.ACTIVE,
                Collections.emptyList(),
                true
        );
    }

    @Test
    @DisplayName("Successfully update valid driver location")
    void testUpdateValidDriverLocation() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("12.97159870"))
                .longitude(new BigDecimal("77.59456270"))
                .heading(new BigDecimal("145.00"))
                .speedKmh(new BigDecimal("40.00"))
                .timestamp(LocalDateTime.now())
                .build();

        DriverLocation savedLocation = DriverLocation.builder()
                .id(100L)
                .driver(driverProfile)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .heading(request.getHeading())
                .speedKmh(request.getSpeedKmh())
                .recordedAt(request.getTimestamp())
                .build();

        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid()))
                .thenReturn(Optional.of(driverProfile));
        when(driverLocationRepository.save(any(DriverLocation.class)))
                .thenReturn(savedLocation);

        LocationUpdateResponse response = driverLocationService.updateDriverLocation(driverPrincipal, request);

        assertNotNull(response);
        assertEquals(driverPrincipal.getUuid(), response.getDriverUuid());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(new BigDecimal("12.97159870"), response.getLatitude());
    }

    @Test
    @DisplayName("Reject invalid latitude outside -90 to +90 bounds")
    void testRejectInvalidLatitude() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("95.00000000"))
                .longitude(new BigDecimal("77.59456270"))
                .build();

        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid()))
                .thenReturn(Optional.of(driverProfile));

        assertThrows(BadRequestException.class, () ->
                driverLocationService.updateDriverLocation(driverPrincipal, request));
    }

    @Test
    @DisplayName("Reject invalid longitude outside -180 to +180 bounds")
    void testRejectInvalidLongitude() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("12.97159870"))
                .longitude(new BigDecimal("-195.00000000"))
                .build();

        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid()))
                .thenReturn(Optional.of(driverProfile));

        assertThrows(BadRequestException.class, () ->
                driverLocationService.updateDriverLocation(driverPrincipal, request));
    }

    @Test
    @DisplayName("Reject future timestamp exceeding 60s clock skew")
    void testRejectFutureTimestamp() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("12.97159870"))
                .longitude(new BigDecimal("77.59456270"))
                .timestamp(LocalDateTime.now().plusMinutes(5))
                .build();

        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid()))
                .thenReturn(Optional.of(driverProfile));

        assertThrows(BadRequestException.class, () ->
                driverLocationService.updateDriverLocation(driverPrincipal, request));
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when driver profile does not exist")
    void testDriverProfileNotFound() {
        LocationUpdateRequest request = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("12.97159870"))
                .longitude(new BigDecimal("77.59456270"))
                .build();

        when(driverProfileRepository.findByUserUuid(driverPrincipal.getUuid()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                driverLocationService.updateDriverLocation(driverPrincipal, request));
    }

    @Test
    @DisplayName("Verify location freshness against max age threshold")
    void testLocationFreshness() {
        DriverLocation freshLoc = DriverLocation.builder()
                .recordedAt(LocalDateTime.now().minusSeconds(60))
                .build();

        DriverLocation staleLoc = DriverLocation.builder()
                .recordedAt(LocalDateTime.now().minusSeconds(600))
                .build();

        assertTrue(driverLocationService.isLocationFresh(freshLoc, 300));
        assertFalse(driverLocationService.isLocationFresh(staleLoc, 300));
        assertFalse(driverLocationService.isLocationFresh(null, 300));
    }
}
