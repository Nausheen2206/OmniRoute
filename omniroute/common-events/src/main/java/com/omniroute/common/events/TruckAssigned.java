package com.omniroute.common.events;

import java.time.Instant;
import java.util.UUID;

public record TruckAssigned(
        String eventId,
        String orderId,
        String truckId,
        double distanceKm,
        Instant timestamp
) implements OmniRouteEvent {
    public TruckAssigned(String orderId, String truckId, double distanceKm) {
        this(UUID.randomUUID().toString(), orderId, truckId, distanceKm, Instant.now());
    }
}
