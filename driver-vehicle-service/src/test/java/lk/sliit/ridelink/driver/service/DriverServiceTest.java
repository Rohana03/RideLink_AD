package lk.sliit.ridelink.driver.service;

import lk.sliit.ridelink.driver.dto.DriverRegistrationRequest;
import lk.sliit.ridelink.driver.dto.DriverResponse;
import lk.sliit.ridelink.driver.dto.EligibleDriverResponse;
import lk.sliit.ridelink.driver.dto.VehicleRequest;
import lk.sliit.ridelink.driver.entity.*;
import lk.sliit.ridelink.driver.exception.DuplicateResourceException;
import lk.sliit.ridelink.driver.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private DriverServiceImpl driverService;

    private Driver mockDriver;
    private Vehicle mockVehicle;
    private DriverRegistrationRequest regRequest;

    @BeforeEach
    void setUp() {
        mockVehicle = Vehicle.builder()
                .make("Toyota")
                .model("Aqua")
                .year(2018)
                .color("Silver")
                .licensePlate("WP CAX-1234")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        mockDriver = Driver.builder()
                .id("driver-doc-100")
                .userId("driver-usr-100")
                .fullName("Sunil Perera")
                .email("sunil@example.com")
                .phoneNumber("+94771234567")
                .licenseNumber("DL-987654")
                .serviceArea("Colombo")
                .serviceRadiusKm(15.0)
                .currentLatitude(6.9271)
                .currentLongitude(79.8612)
                .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
                .operationalStatus(OperationalStatus.ACTIVE)
                .rating(4.9)
                .totalTrips(42)
                .vehicle(mockVehicle)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        VehicleRequest vReq = VehicleRequest.builder()
                .make("Toyota")
                .model("Aqua")
                .year(2018)
                .color("Silver")
                .licensePlate("WP CAX-1234")
                .vehicleType(VehicleType.CAR)
                .seatingCapacity(4)
                .build();

        regRequest = DriverRegistrationRequest.builder()
                .fullName("Sunil Perera")
                .email("sunil@example.com")
                .phoneNumber("+94771234567")
                .licenseNumber("DL-987654")
                .serviceArea("Colombo")
                .serviceRadiusKm(15.0)
                .vehicle(vReq)
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new driver with vehicle")
    void shouldRegisterDriverSuccessfully() {
        when(driverRepository.existsByUserId("driver-usr-100")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("DL-987654")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("WP CAX-1234")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenReturn(mockDriver);

        DriverResponse response = driverService.registerDriver("driver-usr-100", regRequest);

        assertNotNull(response);
        assertEquals("Sunil Perera", response.getFullName());
        assertEquals("WP CAX-1234", response.getVehicle().getLicensePlate());
        assertEquals(DriverAvailabilityStatus.AVAILABLE, response.getAvailabilityStatus());
        verify(driverRepository, times(1)).save(any(Driver.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException if user already has a driver profile")
    void shouldThrowWhenUserIdAlreadyExists() {
        when(driverRepository.existsByUserId("driver-usr-100")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                driverService.registerDriver("driver-usr-100", regRequest));
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update driver availability status")
    void shouldUpdateAvailabilityStatus() {
        when(driverRepository.findByUserId("driver-usr-100")).thenReturn(Optional.of(mockDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(mockDriver);

        driverService.updateAvailability("driver-usr-100", DriverAvailabilityStatus.OFFLINE);

        assertEquals(DriverAvailabilityStatus.OFFLINE, mockDriver.getAvailabilityStatus());
        verify(driverRepository, times(1)).save(mockDriver);
    }

    @Test
    @DisplayName("Should update simulated GPS location")
    void shouldUpdateSimulatedLocation() {
        when(driverRepository.findByUserId("driver-usr-100")).thenReturn(Optional.of(mockDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(mockDriver);

        driverService.updateLocation("driver-usr-100", 6.9000, 79.8500);

        assertEquals(6.9000, mockDriver.getCurrentLatitude());
        assertEquals(79.8500, mockDriver.getCurrentLongitude());
        assertNotNull(mockDriver.getLastLocationUpdate());
        verify(driverRepository, times(1)).save(mockDriver);
    }

    @Test
    @DisplayName("Should retrieve nearest eligible available drivers within radius")
    void shouldFindEligibleDriversWithinRadius() {
        Driver nearbyDriver = Driver.builder()
                .id("driver-doc-200")
                .userId("driver-usr-200")
                .fullName("Kamal Silva")
                .phoneNumber("+94779876543")
                .licenseNumber("DL-112233")
                .currentLatitude(6.9300)
                .currentLongitude(79.8550)
                .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
                .operationalStatus(OperationalStatus.ACTIVE)
                .rating(4.8)
                .totalTrips(15)
                .vehicle(Vehicle.builder()
                        .make("Nissan")
                        .model("Leaf")
                        .vehicleType(VehicleType.CAR)
                        .licensePlate("WP CAD-5678")
                        .seatingCapacity(4)
                        .build())
                .build();

        Driver farDriver = Driver.builder()
                .id("driver-doc-300")
                .userId("driver-usr-300")
                .fullName("Far Away Driver")
                .licenseNumber("DL-999999")
                .currentLatitude(7.2906) // Kandy ~100 km away
                .currentLongitude(80.6337)
                .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
                .operationalStatus(OperationalStatus.ACTIVE)
                .rating(5.0)
                .vehicle(mockVehicle)
                .build();

        when(driverRepository.findAvailableActiveDriversWithLocation(
                DriverAvailabilityStatus.AVAILABLE, OperationalStatus.ACTIVE))
                .thenReturn(List.of(mockDriver, nearbyDriver, farDriver));

        // Search near Colombo Fort (6.9344, 79.8428) with radius 5 km
        List<EligibleDriverResponse> eligibleDrivers = driverService.findEligibleDrivers(
                6.9344, 79.8428, VehicleType.CAR, 5.0, 10);

        assertNotNull(eligibleDrivers);
        assertEquals(2, eligibleDrivers.size(), "Should exclude far driver (>5 km)");
        assertTrue(eligibleDrivers.get(0).getDistanceKm() <= eligibleDrivers.get(1).getDistanceKm(),
                "Drivers must be sorted ascending by distance");
    }

    // ---------------------------------------------------------------------
    // Registration and vehicle failures
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Should treat license plates case-insensitively when checking for duplicates")
    void shouldThrowWhenLicensePlateAlreadyExistsInDifferentCase() {
        regRequest.getVehicle().setLicensePlate("  wp cax-1234 ");
        when(driverRepository.existsByUserId("driver-usr-100")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("DL-987654")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("WP CAX-1234")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                driverService.registerDriver("driver-usr-100", regRequest));
        verify(driverRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should register new drivers as OFFLINE and ACTIVE with an upper-case plate")
    void shouldSaveNewDriverWithSafeDefaults() {
        regRequest.getVehicle().setLicensePlate("wp cax-1234");
        when(driverRepository.existsByUserId("driver-usr-100")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("DL-987654")).thenReturn(false);
        when(driverRepository.existsByVehicleLicensePlate("WP CAX-1234")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenReturn(mockDriver);

        driverService.registerDriver("driver-usr-100", regRequest);

        ArgumentCaptor<Driver> saved = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository).save(saved.capture());
        assertEquals(DriverAvailabilityStatus.OFFLINE, saved.getValue().getAvailabilityStatus());
        assertEquals(OperationalStatus.ACTIVE, saved.getValue().getOperationalStatus());
        assertEquals("WP CAX-1234", saved.getValue().getVehicle().getLicensePlate());
    }

    @Test
    @DisplayName("Should allow re-saving the driver's own plate in a different case")
    void shouldAllowUpdatingVehicleWithOwnPlate() {
        VehicleRequest update = VehicleRequest.builder()
                .make("Toyota").model("Aqua").year(2019).color("Blue")
                .licensePlate("wp cax-1234")
                .vehicleType(VehicleType.CAR).seatingCapacity(4)
                .build();
        when(driverRepository.findByUserId("driver-usr-100")).thenReturn(Optional.of(mockDriver));
        when(driverRepository.save(any(Driver.class))).thenReturn(mockDriver);

        driverService.updateVehicle("driver-usr-100", update);

        assertEquals("Blue", mockDriver.getVehicle().getColor());
        verify(driverRepository, never()).existsByVehicleLicensePlate(any());
    }

    @Test
    @DisplayName("Should reject a vehicle update that uses another driver's plate")
    void shouldThrowWhenUpdatingVehicleToTakenPlate() {
        VehicleRequest update = VehicleRequest.builder()
                .make("Nissan").model("Leaf").year(2020).color("White")
                .licensePlate("WP CAD-5678")
                .vehicleType(VehicleType.CAR).seatingCapacity(4)
                .build();
        when(driverRepository.findByUserId("driver-usr-100")).thenReturn(Optional.of(mockDriver));
        when(driverRepository.existsByVehicleLicensePlate("WP CAD-5678")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () ->
                driverService.updateVehicle("driver-usr-100", update));
        verify(driverRepository, never()).save(any());
    }
}
