package lk.sliit.ridelink.driver.repository;

import lk.sliit.ridelink.driver.entity.Driver;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lk.sliit.ridelink.driver.entity.OperationalStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends MongoRepository<Driver, String> {

    Optional<Driver> findByUserId(String userId);

    Optional<Driver> findByLicenseNumber(String licenseNumber);

    boolean existsByUserId(String userId);

    boolean existsByLicenseNumber(String licenseNumber);

    @Query(value = "{ 'vehicle.licensePlate': ?0 }", exists = true)
    boolean existsByVehicleLicensePlate(String licensePlate);

    List<Driver> findByAvailabilityStatusAndOperationalStatus(
            DriverAvailabilityStatus availabilityStatus,
            OperationalStatus operationalStatus
    );

    @Query("{ 'availabilityStatus': ?0, 'operationalStatus': ?1, 'currentLatitude': { $ne: null }, 'currentLongitude': { $ne: null } }")
    List<Driver> findAvailableActiveDriversWithLocation(
            DriverAvailabilityStatus availabilityStatus,
            OperationalStatus operationalStatus
    );
}
