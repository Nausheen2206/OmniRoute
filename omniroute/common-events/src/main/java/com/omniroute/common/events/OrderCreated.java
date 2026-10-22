package com.omniroute.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        String eventId,
        String orderId,
        String customerId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        double warehouseLat,
        double warehouseLon,
        Instant timestamp
) implements OmniRouteEvent {
    public OrderCreated(String orderId, String customerId, List<OrderItem> items, BigDecimal totalAmount, double warehouseLat, double warehouseLon) {
        this(UUID.randomUUID().toString(), orderId, customerId, items, totalAmount, warehouseLat, warehouseLon, Instant.now());
    }
}
