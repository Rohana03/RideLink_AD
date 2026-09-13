package lk.sliit.ridelink.driver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    private String userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 100)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "license_number", nullable = false, unique = true, length = 50)
    private String licenseNumber;

    @Column(name = "service_area", length = 100)
    @Builder.Default
    private String serviceArea = "Colombo";

    @Column(name = "service_radius_km")
    @Builder.Default
    private Double serviceRadiusKm = 15.0;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    @Builder.Default
    private DriverAvailabilityStatus availabilityStatus = DriverAvailabilityStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 30)
    @Builder.Default
    private OperationalStatus operationalStatus = OperationalStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private Double rating = 5.0;

    @Column(name = "total_trips", nullable = false)
    @Builder.Default
    private Integer totalTrips = 0;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Vehicle vehicle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
        if (vehicle != null) {
            vehicle.setDriver(this);
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.availabilityStatus == null) {
            this.availabilityStatus = DriverAvailabilityStatus.OFFLINE;
        }
        if (this.operationalStatus == null) {
            this.operationalStatus = OperationalStatus.ACTIVE;
        }
        if (this.rating == null) {
            this.rating = 5.0;
        }
        if (this.totalTrips == null) {
            this.totalTrips = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
