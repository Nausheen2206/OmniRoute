package com.omniroute.common.events;

import java.time.Instant;
import java.util.UUID;

public record OrderAllocated(
        String eventId,
        String orderId,
        double warehouseLat,
        double warehouseLon,
        Instant timestamp
) implements OmniRouteEvent {
    public OrderAllocated(String orderId, double warehouseLat, double warehouseLon) {
        this(UUID.randomUUID().toString(), orderId, warehouseLat, warehouseLon, Instant.now());
    }

    public OrderAllocated(String orderId) {
        this(UUID.randomUUID().toString(), orderId, 11.0168, 76.9558, Instant.now());
    }
}
