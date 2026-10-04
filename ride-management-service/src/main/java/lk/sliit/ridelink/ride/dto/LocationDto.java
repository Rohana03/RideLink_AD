package lk.sliit.ridelink.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lk.sliit.ridelink.ride.entity.Location;

public record LocationDto(
        @NotBlank(message = "label is required") String label,
        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be >= -90")
        @DecimalMax(value = "90.0", message = "latitude must be <= 90")
        Double latitude,
        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be >= -180")
        @DecimalMax(value = "180.0", message = "longitude must be <= 180")
        Double longitude
) {
    public Location toEntity() {
        return new Location(label, latitude, longitude);
    }

    public static LocationDto fromEntity(Location location) {
        if (location == null) {
            return null;
        }
        return new LocationDto(location.getLabel(), location.getLatitude(), location.getLongitude());
    }
}
