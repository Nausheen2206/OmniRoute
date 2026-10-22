package com.omniroute.fleet.service;

import com.omniroute.fleet.domain.Assignment;
import com.omniroute.fleet.domain.HaversineDistance;
import com.omniroute.fleet.domain.TruckState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FleetRoutingEngine {

    private final Map<String, TruckState> truckMap = new ConcurrentHashMap<>();
    private final List<Assignment> recentAssignments = new CopyOnWriteArrayList<>();

    @Value("${fleet.stale-timeout-seconds:30}")
    private long staleTimeoutSeconds = 30;

    @Value("${fleet.busy-duration-seconds:60}")
    private long busyDurationSeconds = 60;

    @Value("${fleet.max-returned-trucks:300}")
    private int maxReturnedTrucks = 300;

    @Value("${fleet.hub-lat:11.0168}")
    private double hubLat = 11.0168;

    @Value("${fleet.hub-lon:76.9558}")
    private double hubLon = 76.9558;

    public void updatePosition(String truckId, double lat, double lon, Instant timestamp) {
        truckMap.compute(truckId, (id, current) -> {
            Instant busyUntil = current != null ? current.busyUntil() : null;
            return new TruckState(truckId, lat, lon, timestamp, busyUntil);
        });
    }

    public Optional<Assignment> assignNearestTruck(String orderId, double warehouseLat, double warehouseLon) {
        Instant now = Instant.now();
        TruckState nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (TruckState truck : truckMap.values()) {
            if (truck.isAvailable(now, staleTimeoutSeconds)) {
                double dist = HaversineDistance.calculate(warehouseLat, warehouseLon, truck.lat(), truck.lon());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = truck;
                }
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }

        // Mark truck busy
        Instant busyUntil = now.plus(Duration.ofSeconds(busyDurationSeconds));
        TruckState updated = new TruckState(nearest.truckId(), nearest.lat(), nearest.lon(), nearest.lastUpdate(), busyUntil);
        truckMap.put(nearest.truckId(), updated);

        Assignment assignment = new Assignment(orderId, nearest.truckId(), Math.round(minDistance * 100.0) / 100.0, now);
        recentAssignments.add(0, assignment);
        if (recentAssignments.size() > 100) {
            recentAssignments.remove(recentAssignments.size() - 1);
        }

        return Optional.of(assignment);
    }

    public Collection<TruckState> getAllTrucks() {
        if (truckMap.size() <= maxReturnedTrucks) {
            return Collections.unmodifiableCollection(truckMap.values());
        }

        Instant now = Instant.now();
        List<TruckState> assignedTrucks = new ArrayList<>();
        List<TruckState> availableTrucks = new ArrayList<>();

        for (TruckState truck : truckMap.values()) {
            if (truck.busyUntil() != null && truck.busyUntil().isAfter(now)) {
                assignedTrucks.add(truck);
            } else {
                availableTrucks.add(truck);
            }
        }

        // Sort available trucks by distance to central hub
        availableTrucks.sort(Comparator.comparingDouble(t ->
                HaversineDistance.calculate(hubLat, hubLon, t.lat(), t.lon())));

        int remainingSlots = Math.max(0, maxReturnedTrucks - assignedTrucks.size());
        List<TruckState> result = new ArrayList<>(assignedTrucks);
        result.addAll(availableTrucks.subList(0, Math.min(remainingSlots, availableTrucks.size())));

        return Collections.unmodifiableCollection(result);
    }

    public List<Assignment> getRecentAssignments() {
        return Collections.unmodifiableList(recentAssignments);
    }

    public void clear() {
        truckMap.clear();
        recentAssignments.clear();
    }
}
