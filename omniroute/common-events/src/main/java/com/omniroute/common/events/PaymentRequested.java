package com.omniroute.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRequested(
        String eventId,
        String orderId,
        BigDecimal amount,
        Instant timestamp
) implements OmniRouteEvent {
    public PaymentRequested(String orderId, BigDecimal amount) {
        this(UUID.randomUUID().toString(), orderId, amount, Instant.now());
    }
}
