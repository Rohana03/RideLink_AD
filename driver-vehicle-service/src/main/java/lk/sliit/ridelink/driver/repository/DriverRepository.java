package lk.sliit.ridelink.driver.repository;

import lk.sliit.ridelink.driver.entity.Driver;
import lk.sliit.ridelink.driver.entity.DriverAvailabilityStatus;
import lk.sliit.ridelink.driver.entity.OperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByUserId(String userId);

    Optional<Driver> findByLicenseNumber(String licenseNumber);

    boolean existsByUserId(String userId);

    boolean existsByLicenseNumber(String licenseNumber);

    List<Driver> findByAvailabilityStatusAndOperationalStatus(
            DriverAvailabilityStatus availabilityStatus,
            OperationalStatus operationalStatus
    );

    @Query("SELECT d FROM Driver d WHERE d.availabilityStatus = :avail " +
           "AND d.operationalStatus = :op " +
           "AND d.currentLatitude IS NOT NULL " +
           "AND d.currentLongitude IS NOT NULL")
    List<Driver> findAvailableActiveDriversWithLocation(
            @Param("avail") DriverAvailabilityStatus avail,
            @Param("op") OperationalStatus op
    );
}
