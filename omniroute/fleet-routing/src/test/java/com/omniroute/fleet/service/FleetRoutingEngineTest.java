package com.omniroute.fleet.service;

import com.omniroute.fleet.domain.Assignment;
import com.omniroute.fleet.domain.HaversineDistance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FleetRoutingEngineTest {

    private FleetRoutingEngine routingEngine;
    private static final double WAREHOUSE_LAT = 11.0168;
    private static final double WAREHOUSE_LON = 76.9558;

    @BeforeEach
    void setUp() {
        routingEngine = new FleetRoutingEngine();
        routingEngine.clear();
    }

    @Test
    @DisplayName("HaversineDistance: Zero distance for identical coordinates")
    void haversineZeroForIdenticalCoordinates() {
        double dist = HaversineDistance.calculate(WAREHOUSE_LAT, WAREHOUSE_LON, WAREHOUSE_LAT, WAREHOUSE_LON);
        assertThat(dist).isZero();
    }

    @Test
    @DisplayName("HaversineDistance: Accurate calculation between known points")
    void haversineCalculationAccurate() {
        // Paris (48.8566, 2.3522) to London (51.5074, -0.1278) ~343.5 km
        double dist = HaversineDistance.calculate(48.8566, 2.3522, 51.5074, -0.1278);
        assertThat(dist).isCloseTo(343.5, within(5.0));
    }

    @Test
    @DisplayName("Should select the nearest available truck to the warehouse")
    void shouldSelectNearestTruck() {
        Instant now = Instant.now();

        // Far truck (approx 15 km away)
        routingEngine.updatePosition("TRUCK-FAR", 11.15, 77.05, now);
        // Near truck (approx 2 km away)
        routingEngine.updatePosition("TRUCK-NEAR", 11.03, 76.97, now);
        // Medium truck (approx 6 km away)
        routingEngine.updatePosition("TRUCK-MED", 11.06, 76.99, now);

        Optional<Assignment> assignment = routingEngine.assignNearestTruck("order-001", WAREHOUSE_LAT, WAREHOUSE_LON);

        assertThat(assignment).isPresent();
        assertThat(assignment.get().truckId()).isEqualTo("TRUCK-NEAR");
        assertThat(assignment.get().orderId()).isEqualTo("order-001");
        assertThat(assignment.get().distanceKm()).isLessThan(3.0);
    }

    @Test
    @DisplayName("Should mark assigned truck as busy and select next nearest for second order")
    void shouldPickNextNearestWhenFirstIsBusy() {
        Instant now = Instant.now();

        routingEngine.updatePosition("TRUCK-1", 11.02, 76.96, now); // Closest
        routingEngine.updatePosition("TRUCK-2", 11.05, 76.98, now); // Second closest

        Optional<Assignment> first = routingEngine.assignNearestTruck("order-1", WAREHOUSE_LAT, WAREHOUSE_LON);
        assertThat(first).isPresent();
        assertThat(first.get().truckId()).isEqualTo("TRUCK-1");

        // Immediately assign second order
        Optional<Assignment> second = routingEngine.assignNearestTruck("order-2", WAREHOUSE_LAT, WAREHOUSE_LON);
        assertThat(second).isPresent();
        assertThat(second.get().truckId()).isEqualTo("TRUCK-2");
    }

    @Test
    @DisplayName("Should ignore stale trucks whose last ping exceeded timeout")
    void shouldIgnoreStaleTrucks() {
        Instant staleTime = Instant.now().minus(60, ChronoUnit.SECONDS); // 60s old, timeout is 30s
        routingEngine.updatePosition("TRUCK-STALE", 11.02, 76.96, staleTime);

        Optional<Assignment> assignment = routingEngine.assignNearestTruck("order-stale", WAREHOUSE_LAT, WAREHOUSE_LON);
        assertThat(assignment).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when no trucks exist")
    void shouldReturnEmptyWhenNoTrucks() {
        Optional<Assignment> assignment = routingEngine.assignNearestTruck("order-none", WAREHOUSE_LAT, WAREHOUSE_LON);
        assertThat(assignment).isEmpty();
    }

    @Test
    @DisplayName("Should throttle returned trucks and guarantee inclusion of assigned trucks")
    void shouldThrottleReturnedTrucksAndKeepAssigned() {
        org.springframework.test.util.ReflectionTestUtils.setField(routingEngine, "maxReturnedTrucks", 5);
        Instant now = Instant.now();

        // 10 trucks at different distances
        for (int i = 1; i <= 10; i++) {
            routingEngine.updatePosition("TRUCK-" + i, 11.0 + (i * 0.01), 76.9 + (i * 0.01), now);
        }

        // Assign truck 10 (furthest one)
        routingEngine.assignNearestTruck("order-far", 11.10, 77.00);

        var trucks = routingEngine.getAllTrucks();
        assertThat(trucks).hasSize(5);

        // Assigned truck must be included regardless of distance
        assertThat(trucks).extracting("truckId").contains("TRUCK-10");
    }
}
