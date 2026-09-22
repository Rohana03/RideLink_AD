package lk.sliit.ridelink.driver.service;

import lk.sliit.ridelink.driver.dto.*;
import lk.sliit.ridelink.driver.entity.*;
import lk.sliit.ridelink.driver.exception.DuplicateResourceException;
import lk.sliit.ridelink.driver.exception.ResourceNotFoundException;
import lk.sliit.ridelink.driver.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverServiceImpl implements DriverService {

    private final DriverRepository driverRepository;

    @Override
    public DriverResponse registerDriver(String userId, DriverRegistrationRequest request) {
        log.info("Registering driver profile for userId: {}", userId);

        if (driverRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("Driver profile already exists for user: " + userId);
        }

        if (driverRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new DuplicateResourceException("Driver license number is already registered: " + request.getLicenseNumber());
        }

        VehicleRequest vReq = request.getVehicle();
        String licensePlate = normalizePlate(vReq.getLicensePlate());
        if (driverRepository.existsByVehicleLicensePlate(licensePlate)) {
            throw new DuplicateResourceException("Vehicle license plate is already registered: " + licensePlate);
        }

        Vehicle vehicle = Vehicle.builder()
                .make(vReq.getMake())
                .model(vReq.getModel())
                .year(vReq.getYear())
                .color(vReq.getColor())
                .licensePlate(licensePlate)
                .vehicleType(vReq.getVehicleType())
                .seatingCapacity(vReq.getSeatingCapacity())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Driver driver = Driver.builder()
                .userId(userId)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .licenseNumber(request.getLicenseNumber())
                .serviceArea(request.getServiceArea() != null ? request.getServiceArea() : "Colombo")
                .serviceRadiusKm(request.getServiceRadiusKm() != null ? request.getServiceRadiusKm() : 15.0)
                .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
                .operationalStatus(OperationalStatus.ACTIVE)
                .rating(5.0)
                .totalTrips(0)
                .vehicle(vehicle)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Driver savedDriver = driverRepository.save(driver);
        log.info("Successfully registered driver with ID: {}", savedDriver.getId());
        return DriverResponse.fromEntity(savedDriver);
    }

    @Override
    public DriverResponse getDriverByUserId(String userId) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + userId));
        return DriverResponse.fromEntity(driver);
    }

    @Override
    public DriverResponse getDriverById(String id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));
        return DriverResponse.fromEntity(driver);
    }

    @Override
    public DriverResponse updateProfile(String userId, DriverUpdateRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            driver.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            driver.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            driver.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getServiceArea() != null && !request.getServiceArea().isBlank()) {
            driver.setServiceArea(request.getServiceArea());
        }
        if (request.getServiceRadiusKm() != null && request.getServiceRadiusKm() > 0) {
            driver.setServiceRadiusKm(request.getServiceRadiusKm());
        }
        driver.setUpdatedAt(LocalDateTime.now());

        Driver updatedDriver = driverRepository.save(driver);
        log.info("Updated profile for driver ID: {}", updatedDriver.getId());
        return DriverResponse.fromEntity(updatedDriver);
    }

    @Override
    public DriverResponse updateVehicle(String userId, VehicleRequest request) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + userId));

        Vehicle vehicle = driver.getVehicle();
        if (vehicle == null) {
            vehicle = new Vehicle();
            driver.setVehicle(vehicle);
        }

        // If license plate is changing, ensure uniqueness across other drivers
        String licensePlate = normalizePlate(request.getLicensePlate());
        if (!licensePlate.equals(vehicle.getLicensePlate())
                && driverRepository.existsByVehicleLicensePlate(licensePlate)) {
            throw new DuplicateResourceException("Vehicle license plate is already registered: " + licensePlate);
        }

        vehicle.setMake(request.getMake());
        vehicle.setModel(request.getModel());
        vehicle.setYear(request.getYear());
        vehicle.setColor(request.getColor());
        vehicle.setLicensePlate(licensePlate);
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setSeatingCapacity(request.getSeatingCapacity());
        vehicle.setUpdatedAt(LocalDateTime.now());

        driver.setUpdatedAt(LocalDateTime.now());
        Driver savedDriver = driverRepository.save(driver);
        log.info("Updated vehicle for driver ID: {}", savedDriver.getId());
        return DriverResponse.fromEntity(savedDriver);
    }

    @Override
    public DriverResponse updateAvailability(String userId, DriverAvailabilityStatus status) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + userId));

        driver.setAvailabilityStatus(status);
        driver.setUpdatedAt(LocalDateTime.now());
        Driver savedDriver = driverRepository.save(driver);
        log.info("Updated availability status to {} for driver ID: {}", status, savedDriver.getId());
        return DriverResponse.fromEntity(savedDriver);
    }

    @Override
    public DriverResponse updateLocation(String userId, Double latitude, Double longitude) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + userId));

        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        driver.setLastLocationUpdate(LocalDateTime.now());
        driver.setUpdatedAt(LocalDateTime.now());

        Driver savedDriver = driverRepository.save(driver);
        log.info("Updated simulated location for driver ID: {} to ({}, {})", savedDriver.getId(), latitude, longitude);
        return DriverResponse.fromEntity(savedDriver);
    }

    @Override
    public List<EligibleDriverResponse> findEligibleDrivers(
            Double pickupLat,
            Double pickupLng,
            VehicleType vehicleType,
            Double radiusKm,
            Integer limit
    ) {
        double maxRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 5.0;
        int maxResults = (limit != null && limit > 0) ? limit : 5;

        log.info("Finding eligible drivers near ({}, {}) with radius {} km, vehicleType: {}",
                pickupLat, pickupLng, maxRadius, vehicleType);

        List<Driver> candidateDrivers = driverRepository.findAvailableActiveDriversWithLocation(
                DriverAvailabilityStatus.AVAILABLE,
                OperationalStatus.ACTIVE
        );

        return candidateDrivers.stream()
                .filter(driver -> driver.getVehicle() != null)
                .filter(driver -> vehicleType == null || driver.getVehicle().getVehicleType() == vehicleType)
                .map(driver -> {
                    double distance = GeoUtils.calculateDistanceKm(
                            pickupLat, pickupLng,
                            driver.getCurrentLatitude(), driver.getCurrentLongitude()
                    );
                    return EligibleDriverResponse.fromEntityAndDistance(driver, distance);
                })
                .filter(response -> response.getDistanceKm() <= maxRadius)
                .sorted(Comparator.comparing(EligibleDriverResponse::getDistanceKm))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    @Override
    public DriverResponse updateInternalStatus(String driverId, DriverAvailabilityStatus status) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        driver.setAvailabilityStatus(status);
        driver.setUpdatedAt(LocalDateTime.now());
        Driver savedDriver = driverRepository.save(driver);
        log.info("Interservice updated availability status for driver ID {} to {}", driverId, status);
        return DriverResponse.fromEntity(savedDriver);
    }

    @Override
    public DriverResponse updateInternalLocation(String driverId, Double latitude, Double longitude) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));

        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        driver.setLastLocationUpdate(LocalDateTime.now());
        driver.setUpdatedAt(LocalDateTime.now());

        Driver savedDriver = driverRepository.save(driver);
        log.info("Interservice updated location for driver ID {} to ({}, {})", driverId, latitude, longitude);
        return DriverResponse.fromEntity(savedDriver);
    }

    /** Plates are stored upper-case so "wp cax-1234" and "WP CAX-1234" count as the same vehicle. */
    private static String normalizePlate(String licensePlate) {
        return licensePlate.trim().toUpperCase();
    }
}
