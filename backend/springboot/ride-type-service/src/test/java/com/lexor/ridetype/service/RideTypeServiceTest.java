package com.lexor.ridetype.service;

import com.lexor.ridetype.dto.request.CreateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeRequest;
import com.lexor.ridetype.dto.request.UpdateRideTypeStatusRequest;
import com.lexor.ridetype.dto.response.RideTypeResponse;
import com.lexor.ridetype.entity.RideType;
import com.lexor.ridetype.exception.BadRequestException;
import com.lexor.ridetype.exception.DuplicateResourceException;
import com.lexor.ridetype.exception.ResourceNotFoundException;
import com.lexor.ridetype.repository.RideTypeRepository;
import com.lexor.ridetype.service.impl.RideTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideTypeServiceTest {

    @Mock
    private RideTypeRepository rideTypeRepository;

    @InjectMocks
    private RideTypeServiceImpl rideTypeService;

    private RideType activeRideType1;
    private RideType activeRideType2;
    private RideType disabledRideType;

    @BeforeEach
    void setUp() {
        activeRideType1 = RideType.builder()
                .id(1L)
                .uuid("uuid-1111-go")
                .name("LEXOR Go")
                .code("GO")
                .description("Affordable hatchback")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .cancellationFee(new BigDecimal("30.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/go.png")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        activeRideType2 = RideType.builder()
                .id(2L)
                .uuid("uuid-2222-xl")
                .name("LEXOR XL")
                .code("XL")
                .description("Spacious SUV")
                .baseFare(new BigDecimal("90.00"))
                .perKmRate(new BigDecimal("22.00"))
                .perMinuteRate(new BigDecimal("3.50"))
                .minimumFare(new BigDecimal("140.00"))
                .cancellationFee(new BigDecimal("50.00"))
                .capacity(6)
                .iconUrl("https://cdn.lexor.mobility/icons/xl.png")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        disabledRideType = RideType.builder()
                .id(3L)
                .uuid("uuid-3333-vintage")
                .name("LEXOR Vintage")
                .code("VINTAGE")
                .description("Classic historic cars")
                .baseFare(new BigDecimal("200.00"))
                .perKmRate(new BigDecimal("50.00"))
                .perMinuteRate(new BigDecimal("10.00"))
                .minimumFare(new BigDecimal("300.00"))
                .cancellationFee(new BigDecimal("100.00"))
                .capacity(3)
                .iconUrl("https://cdn.lexor.mobility/icons/vintage.png")
                .isActive(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getEnabledRideTypes returns only active ride types")
    void getEnabledRideTypes_ReturnsActiveOnly() {
        when(rideTypeRepository.findByIsActiveTrueOrderByIdAsc())
                .thenReturn(List.of(activeRideType1, activeRideType2));

        List<RideTypeResponse> result = rideTypeService.getEnabledRideTypes();

        assertEquals(2, result.size());
        assertEquals("GO", result.get(0).getCode());
        assertEquals("XL", result.get(1).getCode());
        assertTrue(result.get(0).getIsActive());
        assertTrue(result.get(1).getIsActive());
    }

    @Test
    @DisplayName("getEnabledRideTypeByUuid succeeds when ride type is active")
    void getEnabledRideTypeByUuid_WhenActive_ReturnsResponse() {
        when(rideTypeRepository.findByUuidAndIsActiveTrue("uuid-1111-go"))
                .thenReturn(Optional.of(activeRideType1));

        RideTypeResponse response = rideTypeService.getEnabledRideTypeByUuid("uuid-1111-go");

        assertNotNull(response);
        assertEquals("uuid-1111-go", response.getUuid());
        assertEquals("LEXOR Go", response.getName());
    }

    @Test
    @DisplayName("getEnabledRideTypeByUuid throws ResourceNotFoundException when UUID not found or inactive")
    void getEnabledRideTypeByUuid_WhenNotFoundOrInactive_ThrowsException() {
        when(rideTypeRepository.findByUuidAndIsActiveTrue("non-existent-uuid"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rideTypeService.getEnabledRideTypeByUuid("non-existent-uuid"));
    }

    @Test
    @DisplayName("getAllRideTypesAdmin returns both active and disabled ride types")
    void getAllRideTypesAdmin_ReturnsAll() {
        when(rideTypeRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(activeRideType1, activeRideType2, disabledRideType));

        List<RideTypeResponse> result = rideTypeService.getAllRideTypesAdmin();

        assertEquals(3, result.size());
        assertEquals("VINTAGE", result.get(2).getCode());
        assertFalse(result.get(2).getIsActive());
    }

    @Test
    @DisplayName("getRideTypeByUuidAdmin retrieves disabled ride type")
    void getRideTypeByUuidAdmin_RetrievesDisabled() {
        when(rideTypeRepository.findByUuid("uuid-3333-vintage"))
                .thenReturn(Optional.of(disabledRideType));

        RideTypeResponse response = rideTypeService.getRideTypeByUuidAdmin("uuid-3333-vintage");

        assertNotNull(response);
        assertEquals("VINTAGE", response.getCode());
        assertFalse(response.getIsActive());
    }

    @Test
    @DisplayName("createRideType successfully saves and normalizes code to uppercase and trims name")
    void createRideType_SucceedsAndNormalizes() {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("  LEXOR Luxury  ")
                .code("luxury_prime")
                .description("Super luxury tier")
                .baseFare(new BigDecimal("150.00"))
                .perKmRate(new BigDecimal("30.00"))
                .perMinuteRate(new BigDecimal("5.00"))
                .minimumFare(new BigDecimal("200.00"))
                .cancellationFee(new BigDecimal("80.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/luxury.png")
                .isActive(true)
                .build();

        when(rideTypeRepository.existsByCodeIgnoreCase("LUXURY_PRIME")).thenReturn(false);
        when(rideTypeRepository.existsByNameIgnoreCase("LEXOR Luxury")).thenReturn(false);
        when(rideTypeRepository.save(any(RideType.class))).thenAnswer(invocation -> {
            RideType r = invocation.getArgument(0);
            r.setId(10L);
            return r;
        });

        RideTypeResponse response = rideTypeService.createRideType(request);

        assertNotNull(response);
        assertEquals("LEXOR Luxury", response.getName());
        assertEquals("LUXURY_PRIME", response.getCode());
        assertNotNull(response.getUuid());
        assertTrue(response.getIsActive());
    }

    @Test
    @DisplayName("createRideType throws DuplicateResourceException on duplicate code")
    void createRideType_DuplicateCode_ThrowsException() {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("New Go")
                .code("go")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .capacity(4)
                .build();

        when(rideTypeRepository.existsByCodeIgnoreCase("GO")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> rideTypeService.createRideType(request));
        verify(rideTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRideType throws DuplicateResourceException on duplicate name")
    void createRideType_DuplicateName_ThrowsException() {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("LEXOR Go")
                .code("GO_NEW")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .capacity(4)
                .build();

        when(rideTypeRepository.existsByCodeIgnoreCase("GO_NEW")).thenReturn(false);
        when(rideTypeRepository.existsByNameIgnoreCase("LEXOR Go")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> rideTypeService.createRideType(request));
        verify(rideTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRideType throws BadRequestException when minimumFare < baseFare")
    void createRideType_MinimumFareLessThanBaseFare_ThrowsException() {
        CreateRideTypeRequest request = CreateRideTypeRequest.builder()
                .name("Invalid Fare")
                .code("INVALID")
                .baseFare(new BigDecimal("100.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("50.00")) // Invalid: min fare < base fare
                .capacity(4)
                .build();

        assertThrows(BadRequestException.class, () -> rideTypeService.createRideType(request));
    }

    @Test
    @DisplayName("updateRideType updates permitted fields and normalizes")
    void updateRideType_Succeeds() {
        UpdateRideTypeRequest request = UpdateRideTypeRequest.builder()
                .name("LEXOR Go Prime")
                .description("Enhanced hatchback rides")
                .baseFare(new BigDecimal("55.00"))
                .perKmRate(new BigDecimal("16.00"))
                .perMinuteRate(new BigDecimal("2.60"))
                .minimumFare(new BigDecimal("85.00"))
                .cancellationFee(new BigDecimal("35.00"))
                .capacity(4)
                .iconUrl("https://cdn.lexor.mobility/icons/go-prime.png")
                .build();

        when(rideTypeRepository.findByUuid("uuid-1111-go")).thenReturn(Optional.of(activeRideType1));
        when(rideTypeRepository.existsByNameIgnoreCaseAndUuidNot("LEXOR Go Prime", "uuid-1111-go")).thenReturn(false);
        when(rideTypeRepository.save(any(RideType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideTypeResponse response = rideTypeService.updateRideType("uuid-1111-go", request);

        assertEquals("LEXOR Go Prime", response.getName());
        assertEquals("GO", response.getCode());
        assertEquals(new BigDecimal("55.00"), response.getBaseFare());
    }

    @Test
    @DisplayName("updateRideType throws ResourceNotFoundException on unknown UUID")
    void updateRideType_UnknownUuid_ThrowsException() {
        UpdateRideTypeRequest request = UpdateRideTypeRequest.builder()
                .name("Updated Name")
                .baseFare(new BigDecimal("50.00"))
                .perKmRate(new BigDecimal("15.00"))
                .perMinuteRate(new BigDecimal("2.50"))
                .minimumFare(new BigDecimal("80.00"))
                .capacity(4)
                .build();

        when(rideTypeRepository.findByUuid("missing-uuid")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> rideTypeService.updateRideType("missing-uuid", request));
    }

    @Test
    @DisplayName("updateRideTypeStatus enables a disabled ride type")
    void updateRideTypeStatus_Enables() {
        UpdateRideTypeStatusRequest request = UpdateRideTypeStatusRequest.builder()
                .isActive(true)
                .reason("Reactivating vintage service")
                .build();

        when(rideTypeRepository.findByUuid("uuid-3333-vintage")).thenReturn(Optional.of(disabledRideType));
        when(rideTypeRepository.save(any(RideType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideTypeResponse response = rideTypeService.updateRideTypeStatus("uuid-3333-vintage", request);

        assertTrue(response.getIsActive());
    }

    @Test
    @DisplayName("updateRideTypeStatus prevents disabling the only active ride type")
    void updateRideTypeStatus_PreventsDisablingLastActive() {
        UpdateRideTypeStatusRequest request = UpdateRideTypeStatusRequest.builder()
                .isActive(false)
                .reason("Maintenance")
                .build();

        when(rideTypeRepository.findByUuid("uuid-1111-go")).thenReturn(Optional.of(activeRideType1));
        when(rideTypeRepository.countByIsActiveTrue()).thenReturn(1L);

        assertThrows(BadRequestException.class,
                () -> rideTypeService.updateRideTypeStatus("uuid-1111-go", request));
    }

    @Test
    @DisplayName("deleteRideTypeSafely rejects hard deletion to protect referential integrity")
    void deleteRideTypeSafely_RejectsHardDeletion() {
        when(rideTypeRepository.findByUuid("uuid-1111-go")).thenReturn(Optional.of(activeRideType1));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> rideTypeService.deleteRideTypeSafely("uuid-1111-go"));

        assertTrue(ex.getMessage().contains("Hard deletion of ride categories is forbidden"));
    }
}
