package com.lexor.vehicle.service;

import com.lexor.vehicle.dto.request.RegisterVehicleRequest;
import com.lexor.vehicle.dto.request.UpdateVehicleRequest;
import com.lexor.vehicle.dto.response.VehicleResponse;
import com.lexor.vehicle.dto.response.VehicleVerificationStatusResponse;
import com.lexor.vehicle.entity.DocumentVerificationStatus;
import com.lexor.vehicle.entity.DriverProfile;
import com.lexor.vehicle.entity.DriverStatus;
import com.lexor.vehicle.entity.FuelType;
import com.lexor.vehicle.entity.OnboardingStatus;
import com.lexor.vehicle.entity.Role;
import com.lexor.vehicle.entity.User;
import com.lexor.vehicle.entity.UserStatus;
import com.lexor.vehicle.entity.Vehicle;
import com.lexor.vehicle.entity.VehicleDocument;
import com.lexor.vehicle.entity.VehicleDocumentType;
import com.lexor.vehicle.entity.VehicleVerificationStatus;
import com.lexor.vehicle.exception.BadRequestException;
import com.lexor.vehicle.exception.DuplicateResourceException;
import com.lexor.vehicle.exception.ForbiddenException;
import com.lexor.vehicle.exception.InvalidStatusTransitionException;
import com.lexor.vehicle.repository.DriverProfileRepository;
import com.lexor.vehicle.repository.DriverVehicleAssignmentRepository;
import com.lexor.vehicle.repository.UserRepository;
import com.lexor.vehicle.repository.VehicleDocumentRepository;
import com.lexor.vehicle.repository.VehicleRepository;
import com.lexor.vehicle.repository.VehicleVerificationHistoryRepository;
import com.lexor.vehicle.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VehicleServiceTest {

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleDocumentRepository documentRepository;

    @Autowired
    private DriverProfileRepository driverProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverVehicleAssignmentRepository assignmentRepository;

    @Autowired
    private VehicleVerificationHistoryRepository historyRepository;

    private User driverUser1;
    private User driverUser2;
    private DriverProfile driverProfile1;
    private DriverProfile driverProfile2;
    private UserPrincipal driverPrincipal1;
    private UserPrincipal driverPrincipal2;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        assignmentRepository.deleteAll();
        documentRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverProfileRepository.deleteAll();
        userRepository.deleteAll();

        driverUser1 = User.builder()
                .uuid("driver-u-1")
                .firstName("Vikram")
                .lastName("Singh")
                .email("vikram@lexor.mobility")
                .phoneNumber("+919876543201")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser1 = userRepository.save(driverUser1);

        driverProfile1 = DriverProfile.builder()
                .user(driverUser1)
                .licenseNumber("KA-01-20230009876")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile1 = driverProfileRepository.save(driverProfile1);
        driverPrincipal1 = UserPrincipal.create(driverUser1);

        driverUser2 = User.builder()
                .uuid("driver-u-2")
                .firstName("Amit")
                .lastName("Sharma")
                .email("amit@lexor.mobility")
                .phoneNumber("+919876543202")
                .passwordHash("hashed")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .build();
        driverUser2 = userRepository.save(driverUser2);

        driverProfile2 = DriverProfile.builder()
                .user(driverUser2)
                .licenseNumber("DL-04-20230001234")
                .status(DriverStatus.OFFLINE)
                .onboardingStatus(OnboardingStatus.VERIFIED)
                .isVerified(true)
                .build();
        driverProfile2 = driverProfileRepository.save(driverProfile2);
        driverPrincipal2 = UserPrincipal.create(driverUser2);
    }

    @Test
    @DisplayName("Successfully register a vehicle with normalized license plate")
    void testRegisterVehicleSuccess() {
        RegisterVehicleRequest request = RegisterVehicleRequest.builder()
                .make("Hyundai")
                .model("Creta")
                .year(2024)
                .color("Polar White")
                .licensePlate("ka 05 mw 1234")
                .vin("MAL12345678901234")
                .rideTypeCategory("SUV")
                .seatCapacity(5)
                .fuelType(FuelType.PETROL)
                .build();

        VehicleResponse response = vehicleService.registerVehicle(driverPrincipal1, request);

        assertNotNull(response);
        assertNotNull(response.getUuid());
        assertEquals("KA-05-MW-1234", response.getLicensePlate());
        assertEquals(VehicleVerificationStatus.PENDING_VERIFICATION, response.getVerificationStatus());
        assertTrue(response.isActive());
        assertEquals(driverUser1.getUuid(), response.getDriverUuid());
    }

    @Test
    @DisplayName("Reject duplicate registration number (license plate)")
    void testRejectDuplicateLicensePlate() {
        RegisterVehicleRequest request1 = RegisterVehicleRequest.builder()
                .make("Toyota")
                .model("Innova")
                .year(2022)
                .color("Silver")
                .licensePlate("KA-01-EQ-4829")
                .rideTypeCategory("XL")
                .seatCapacity(7)
                .fuelType(FuelType.DIESEL)
                .build();
        vehicleService.registerVehicle(driverPrincipal1, request1);

        RegisterVehicleRequest request2 = RegisterVehicleRequest.builder()
                .make("Honda")
                .model("City")
                .year(2023)
                .color("Red")
                .licensePlate("ka 01 eq 4829") // normalized to same
                .rideTypeCategory("SEDAN")
                .seatCapacity(5)
                .fuelType(FuelType.PETROL)
                .build();

        assertThrows(DuplicateResourceException.class, () ->
                vehicleService.registerVehicle(driverPrincipal2, request2));
    }

    @Test
    @DisplayName("Reject duplicate VIN")
    void testRejectDuplicateVin() {
        RegisterVehicleRequest request1 = RegisterVehicleRequest.builder()
                .make("Toyota")
                .model("Innova")
                .year(2022)
                .color("Silver")
                .licensePlate("KA-01-EQ-1111")
                .vin("VIN12345678901234")
                .rideTypeCategory("SUV")
                .seatCapacity(7)
                .fuelType(FuelType.DIESEL)
                .build();
        vehicleService.registerVehicle(driverPrincipal1, request1);

        RegisterVehicleRequest request2 = RegisterVehicleRequest.builder()
                .make("Honda")
                .model("City")
                .year(2023)
                .color("Red")
                .licensePlate("KA-01-EQ-2222")
                .vin("VIN12345678901234") // same VIN
                .rideTypeCategory("SEDAN")
                .seatCapacity(5)
                .fuelType(FuelType.PETROL)
                .build();

        assertThrows(DuplicateResourceException.class, () ->
                vehicleService.registerVehicle(driverPrincipal2, request2));
    }

    @Test
    @DisplayName("Enforce driver ownership: other driver cannot view or update vehicle")
    void testDriverOwnershipCheck() {
        RegisterVehicleRequest request = RegisterVehicleRequest.builder()
                .make("Tata")
                .model("Nexon EV")
                .year(2023)
                .color("Teal Blue")
                .licensePlate("KA-03-EV-9999")
                .rideTypeCategory("GREEN")
                .seatCapacity(5)
                .fuelType(FuelType.ELECTRIC)
                .build();
        VehicleResponse registered = vehicleService.registerVehicle(driverPrincipal1, request);

        // Driver 2 attempts to get or update Driver 1's vehicle
        assertThrows(ForbiddenException.class, () ->
                vehicleService.getVehicleByUuid(driverPrincipal2, registered.getUuid()));

        UpdateVehicleRequest updateReq = UpdateVehicleRequest.builder()
                .color("Black")
                .build();

        assertThrows(ForbiddenException.class, () ->
                vehicleService.updateVehicle(driverPrincipal2, registered.getUuid(), updateReq));
    }

    @Test
    @DisplayName("Submit for verification fails if mandatory documents are missing")
    void testSubmitVerificationMissingDocuments() {
        RegisterVehicleRequest request = RegisterVehicleRequest.builder()
                .make("Maruti")
                .model("Swift")
                .year(2022)
                .color("Grey")
                .licensePlate("KA-02-AB-5555")
                .rideTypeCategory("MINI")
                .seatCapacity(4)
                .fuelType(FuelType.CNG)
                .build();
        VehicleResponse registered = vehicleService.registerVehicle(driverPrincipal1, request);

        // Submit for verification without documents uploaded
        assertThrows(BadRequestException.class, () ->
                vehicleService.submitForVerification(driverPrincipal1, registered.getUuid()));
    }

    @Test
    @DisplayName("Cannot update vehicle when suspended")
    void testCannotUpdateSuspendedVehicle() {
        RegisterVehicleRequest request = RegisterVehicleRequest.builder()
                .make("Maruti")
                .model("Baleno")
                .year(2022)
                .color("Blue")
                .licensePlate("KA-04-XY-7777")
                .rideTypeCategory("MINI")
                .seatCapacity(4)
                .fuelType(FuelType.PETROL)
                .build();
        VehicleResponse registered = vehicleService.registerVehicle(driverPrincipal1, request);

        Vehicle vehicle = vehicleRepository.findByUuid(registered.getUuid()).orElseThrow();
        vehicle.setVerificationStatus(VehicleVerificationStatus.SUSPENDED);
        vehicleRepository.save(vehicle);

        UpdateVehicleRequest updateReq = UpdateVehicleRequest.builder().color("Yellow").build();
        assertThrows(InvalidStatusTransitionException.class, () ->
                vehicleService.updateVehicle(driverPrincipal1, registered.getUuid(), updateReq));
    }

    @Test
    @DisplayName("Cannot activate an unverified vehicle")
    void testCannotActivateUnverifiedVehicle() {
        RegisterVehicleRequest request = RegisterVehicleRequest.builder()
                .make("Maruti")
                .model("Ciaz")
                .year(2021)
                .color("Brown")
                .licensePlate("KA-04-XY-8888")
                .rideTypeCategory("SEDAN")
                .seatCapacity(5)
                .fuelType(FuelType.PETROL)
                .build();
        VehicleResponse registered = vehicleService.registerVehicle(driverPrincipal1, request);

        // Attempt to activate when status is PENDING_VERIFICATION
        assertThrows(InvalidStatusTransitionException.class, () ->
                vehicleService.updateVehicleStatus(driverPrincipal1, registered.getUuid(), true));
    }
}
