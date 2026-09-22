package lk.sliit.ridelink.driver.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    @DisplayName("Should return 0 when both coordinates are identical")
    void shouldReturnZeroForIdenticalCoordinates() {
        double distance = GeoUtils.calculateDistanceKm(6.9271, 79.8612, 6.9271, 79.8612);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("Should calculate accurate distance between Colombo Fort and Bambalapitiya (~4.7 km)")
    void shouldCalculateAccurateDistanceBetweenLocations() {
        // Colombo Fort (6.9344, 79.8428) to Bambalapitiya (6.8937, 79.8553)
        double distance = GeoUtils.calculateDistanceKm(6.9344, 79.8428, 6.8937, 79.8553);
        assertTrue(distance > 4.5 && distance < 5.0, "Expected distance ~4.7 km, got: " + distance);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when any coordinate is null")
    void shouldThrowExceptionWhenCoordinateIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                GeoUtils.calculateDistanceKm(null, 79.8612, 6.9271, 79.8612));
        assertThrows(IllegalArgumentException.class, () ->
                GeoUtils.calculateDistanceKm(6.9271, null, 6.9271, 79.8612));
    }
}
