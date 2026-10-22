package com.omniroute.common.events;

import java.time.Instant;
import java.util.UUID;

public record InventoryLocked(
        String eventId,
        String orderId,
        Instant timestamp
) implements OmniRouteEvent {
    public InventoryLocked(String orderId) {
        this(UUID.randomUUID().toString(), orderId, Instant.now());
    }
}
