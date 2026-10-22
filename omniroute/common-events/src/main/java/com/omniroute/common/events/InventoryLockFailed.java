package com.omniroute.common.events;

import java.time.Instant;
import java.util.UUID;

public record InventoryLockFailed(
        String eventId,
        String orderId,
        String reason,
        Instant timestamp
) implements OmniRouteEvent {
    public InventoryLockFailed(String orderId, String reason) {
        this(UUID.randomUUID().toString(), orderId, reason, Instant.now());
    }
}
