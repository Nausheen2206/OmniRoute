package com.omniroute.fleet.domain;

import java.time.Instant;

public record TruckState(
        String truckId,
        double lat,
        double lon,
        Instant lastUpdate,
        Instant busyUntil
) {
    public boolean isAvailable(Instant now, long maxStaleSeconds) {
        if (busyUntil != null && busyUntil.isAfter(now)) {
            return false;
        }
        return lastUpdate != null && lastUpdate.isAfter(now.minusSeconds(maxStaleSeconds));
    }
}
