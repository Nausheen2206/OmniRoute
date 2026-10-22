package com.omniroute.common.events;

import java.time.Instant;
import java.util.UUID;

public record RefundPayment(
        String eventId,
        String orderId,
        Instant timestamp
) implements OmniRouteEvent {
    public RefundPayment(String orderId) {
        this(UUID.randomUUID().toString(), orderId, Instant.now());
    }
}
