package com.omniroute.common.events;

import java.time.Instant;
import java.util.UUID;

public record TruckPositionUpdated(
        String eventId,
        String truckId,
        double lat,
        double lon,
        Instant timestamp
) implements OmniRouteEvent {
    public TruckPositionUpdated(String truckId, double lat, double lon) {
        this(UUID.randomUUID().toString(), truckId, lat, lon, Instant.now());
    }

    @Override
    public String orderId() {
        return "";
    }
}
