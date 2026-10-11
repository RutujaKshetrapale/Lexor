package com.lexor.driver.service;

import com.lexor.driver.dto.EmergencyContactDto;
import com.lexor.driver.dto.request.DriverAvailabilityRequest;
import com.lexor.driver.dto.request.PatchDriverProfileRequest;
import com.lexor.driver.dto.request.UpdateDriverProfileRequest;
import com.lexor.driver.dto.response.DriverAvailabilityResponse;
import com.lexor.driver.dto.response.DriverProfileResponse;
import com.lexor.driver.entity.DriverProfile;
import com.lexor.driver.entity.DriverStatus;
import com.lexor.driver.entity.EmergencyContact;
import com.lexor.driver.entity.OnboardingStatus;
import com.lexor.driver.entity.Role;
import com.lexor.driver.entity.User;
import com.lexor.driver.entity.UserStatus;
import com.lexor.driver.exception.BadRequestException;
import com.lexor.driver.exception.DuplicateResourceException;
import com.lexor.driver.exception.ForbiddenException;
import com.lexor.driver.repository.DriverProfileRepository;
import com.lexor.driver.repository.DriverVerificationHistoryRepository;
import com.lexor.driver.repository.EmergencyContactRepository;
import com.lexor.driver.repository.UserRepository;
import com.lexor.driver.security.UserPrincipal;
import com.lexor.driver.service.impl.DriverProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmergencyContactRepository emergencyContactRepository;

    @Mock
    private OnboardingPolicyService onboardingPolicyService;

    @Mock
    private DriverVerificationHistoryRepository verificationHistoryRepository;

    @InjectMocks
    private DriverProfileServiceImpl driverProfileService;

    private User driverUser;
    private DriverProfile driverProfile;
    private UserPrincipal driverPrincipal;

    @BeforeEach
    void setUp() {
        driverUser = User.builder()
                .id(1L)
                .uuid("driver-uuid-1")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram@example.com")
                .phoneNumber("+919876543210")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();

        driverProfile = DriverProfile.builder()
                .id(10L)
                .user(driverUser)
                .licenseNumber("KA-01-20230009876")
                .licenseExpiryDate(LocalDate.now().plusYears(3))
                .licenseIssueDate(LocalDate.now().minusYears(1))
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .residentialAddress("MG Road")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560038")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.INCOMPLETE)
                .isVerified(false)
                .build();

        driverPrincipal = new UserPrincipal(
                driverUser.getId(),
                driverUser.getUuid(),
                driverUser.getFirstName(),
                driverUser.getLastName(),
                driverUser.getEmail(),
                "password",
                Role.DRIVER,
                UserStatus.ACTIVE,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_DRIVER")),
                true
        );
    }

    @Test
    @DisplayName("Should return driver profile response")
    void testGetDriverProfile_Success() {
        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(1L)).thenReturn(Optional.of(driverProfile));

        DriverProfileResponse response = driverProfileService.getDriverProfile(driverPrincipal);

        assertNotNull(response);
        assertEquals("KA-01-20230009876", response.getLicenseNumber());
        assertEquals("Vikram", response.getFirstName());
    }

    @Test
    @DisplayName("Should reject update when expiry date is not after issue date")
    void testUpdateDriverProfile_InvalidDates() {
        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(1L)).thenReturn(Optional.of(driverProfile));

        UpdateDriverProfileRequest request = UpdateDriverProfileRequest.builder()
                .licenseNumber("KA-01-20240001111")
                .licenseIssueDate(LocalDate.of(2025, 1, 1))
                .licenseExpiryDate(LocalDate.of(2024, 1, 1)) // before issue date!
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .residentialAddress("Indiranagar")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560038")
                .build();

        assertThrows(BadRequestException.class, () -> driverProfileService.updateDriverProfile(driverPrincipal, request));
    }

    @Test
    @DisplayName("Should reject update when license number is already registered by another driver")
    void testUpdateDriverProfile_DuplicateLicense() {
        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(1L)).thenReturn(Optional.of(driverProfile));
        when(driverProfileRepository.existsByLicenseNumberAndIdNot("KA-01-DUPLICATE", 10L)).thenReturn(true);

        UpdateDriverProfileRequest request = UpdateDriverProfileRequest.builder()
                .licenseNumber("KA-01-DUPLICATE")
                .licenseIssueDate(LocalDate.of(2020, 1, 1))
                .licenseExpiryDate(LocalDate.of(2028, 1, 1))
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .residentialAddress("Indiranagar")
                .city("Bengaluru")
                .state("Karnataka")
                .postalCode("560038")
                .build();

        assertThrows(DuplicateResourceException.class, () -> driverProfileService.updateDriverProfile(driverPrincipal, request));
    }

    @Test
    @DisplayName("Should patch only supplied fields without clearing omitted values")
    void testPatchDriverProfile_Success() {
        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(1L)).thenReturn(Optional.of(driverProfile));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        PatchDriverProfileRequest request = PatchDriverProfileRequest.builder()
                .city("Mysuru")
                .build();

        DriverProfileResponse response = driverProfileService.patchDriverProfile(driverPrincipal, request);

        assertEquals("Mysuru", response.getCity());
        assertEquals("KA-01-20230009876", response.getLicenseNumber()); // preserved!
        assertEquals("MG Road", response.getResidentialAddress()); // preserved!
    }

    @Test
    @DisplayName("Should allow verified driver with active account to go ONLINE")
    void testUpdateAvailability_OnlineAllowed() {
        driverProfile.setVerified(true);
        driverProfile.setOnboardingStatus(OnboardingStatus.VERIFIED);

        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(1L)).thenReturn(Optional.of(driverProfile));

        DriverAvailabilityRequest request = DriverAvailabilityRequest.builder()
                .status(DriverStatus.ONLINE)
                .build();

        DriverAvailabilityResponse response = driverProfileService.updateAvailability(driverPrincipal, request);

        assertTrue(response.isOnline());
        assertEquals(DriverStatus.ONLINE, response.getStatus());
    }

    @Test
    @DisplayName("Should forbid unverified driver from going ONLINE")
    void testUpdateAvailability_OnlineDeniedWhenUnverified() {
        driverProfile.setVerified(false);
        driverProfile.setOnboardingStatus(OnboardingStatus.INCOMPLETE);

        when(userRepository.findByUuid("driver-uuid-1")).thenReturn(Optional.of(driverUser));
        when(driverProfileRepository.findByUserId(1L)).thenReturn(Optional.of(driverProfile));

        DriverAvailabilityRequest request = DriverAvailabilityRequest.builder()
                .status(DriverStatus.ONLINE)
                .build();

        assertThrows(ForbiddenException.class, () -> driverProfileService.updateAvailability(driverPrincipal, request));
    }
}
