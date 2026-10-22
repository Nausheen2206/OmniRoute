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
        return Collections.unmodifiableCollection(truckMap.values());
    }

    public List<Assignment> getRecentAssignments() {
        return Collections.unmodifiableList(recentAssignments);
    }

    public void clear() {
        truckMap.clear();
        recentAssignments.clear();
    }
}
