package com.omniroute.fleet.streams.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TruckState(
        String truckId,
        double lat,
        double lon,
        Instant lastUpdate,
        Instant busyUntil,
        String cellId
) {
    public TruckState(String truckId, double lat, double lon, Instant lastUpdate, Instant busyUntil) {
        this(truckId, lat, lon, lastUpdate, busyUntil, null);
    }

    public boolean isAvailable(Instant now, long maxStaleSeconds) {
        if (busyUntil != null && busyUntil.isAfter(now)) {
            return false;
        }
        return lastUpdate != null && lastUpdate.isAfter(now.minusSeconds(maxStaleSeconds));
    }
}
