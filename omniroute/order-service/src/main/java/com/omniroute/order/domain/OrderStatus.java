package com.omniroute.order.domain;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    PENDING,
    INVENTORY_LOCKED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    COMPENSATING,
    COMPLETED,
    FAILED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, Set.of(INVENTORY_LOCKED, FAILED),
            INVENTORY_LOCKED, Set.of(PAYMENT_PENDING, PAYMENT_COMPLETED, COMPENSATING, FAILED),
            PAYMENT_PENDING, Set.of(PAYMENT_COMPLETED, COMPENSATING, FAILED),
            PAYMENT_COMPLETED, Set.of(COMPLETED, COMPENSATING, FAILED),
            COMPENSATING, Set.of(FAILED),
            COMPLETED, Set.of(),
            FAILED, Set.of()
    );

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
