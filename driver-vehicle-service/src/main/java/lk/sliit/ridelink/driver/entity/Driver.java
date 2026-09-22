package lk.sliit.ridelink.driver.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "drivers")
@CompoundIndex(name = "vehicle_license_plate_unique", def = "{'vehicle.licensePlate': 1}", unique = true, sparse = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    private String id;

    /** Optimistic lock: a concurrent save of a stale copy fails instead of overwriting. */
    @Version
    private Long version;

    @Indexed(unique = true)
    private String userId;

    private String fullName;

    private String email;

    private String phoneNumber;

    @Indexed(unique = true)
    private String licenseNumber;

    @Builder.Default
    private String serviceArea = "Colombo";

    @Builder.Default
    private Double serviceRadiusKm = 15.0;

    private Double currentLatitude;

    private Double currentLongitude;

    private LocalDateTime lastLocationUpdate;

    @Builder.Default
    private DriverAvailabilityStatus availabilityStatus = DriverAvailabilityStatus.OFFLINE;

    @Builder.Default
    private OperationalStatus operationalStatus = OperationalStatus.ACTIVE;

    @Builder.Default
    private Double rating = 5.0;

    @Builder.Default
    private Integer totalTrips = 0;

    private Vehicle vehicle;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
