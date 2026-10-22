package com.omniroute.order.service;

import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;
    private Order order;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
        order = new Order(UUID.randomUUID().toString(), "CUST-1", "[]", BigDecimal.valueOf(100), OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should stay in same state idempotently without error")
    void shouldAllowSameStateIdempotent() {
        boolean transitioned = stateMachine.transition(order, OrderStatus.PENDING);
        assertThat(transitioned).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @ParameterizedTest(name = "Allowed: {0} -> {1}")
    @CsvSource({
            "PENDING, INVENTORY_LOCKED",
            "PENDING, FAILED",
            "INVENTORY_LOCKED, PAYMENT_PENDING",
            "INVENTORY_LOCKED, PAYMENT_COMPLETED",
            "INVENTORY_LOCKED, COMPENSATING",
            "INVENTORY_LOCKED, FAILED",
            "PAYMENT_PENDING, PAYMENT_COMPLETED",
            "PAYMENT_PENDING, COMPENSATING",
            "PAYMENT_PENDING, FAILED",
            "PAYMENT_COMPLETED, COMPLETED",
            "PAYMENT_COMPLETED, COMPENSATING",
            "PAYMENT_COMPLETED, FAILED",
            "COMPENSATING, FAILED"
    })
    void shouldAllowValidTransitions(OrderStatus from, OrderStatus to) {
        order.setStatus(from);
        boolean result = stateMachine.transition(order, to);
        assertThat(result).isTrue();
        assertThat(order.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "Rejected: {0} -> {1}")
    @CsvSource({
            "PENDING, COMPLETED",
            "PENDING, PAYMENT_COMPLETED",
            "PENDING, PAYMENT_PENDING",
            "PENDING, COMPENSATING",
            "INVENTORY_LOCKED, PENDING",
            "INVENTORY_LOCKED, COMPLETED",
            "PAYMENT_PENDING, PENDING",
            "PAYMENT_PENDING, INVENTORY_LOCKED",
            "PAYMENT_PENDING, COMPLETED",
            "PAYMENT_COMPLETED, PENDING",
            "PAYMENT_COMPLETED, INVENTORY_LOCKED",
            "PAYMENT_COMPLETED, PAYMENT_PENDING",
            "COMPLETED, PENDING",
            "COMPLETED, INVENTORY_LOCKED",
            "COMPLETED, PAYMENT_PENDING",
            "COMPLETED, PAYMENT_COMPLETED",
            "COMPLETED, COMPENSATING",
            "COMPLETED, FAILED",
            "FAILED, PENDING",
            "FAILED, INVENTORY_LOCKED",
            "FAILED, PAYMENT_PENDING",
            "FAILED, PAYMENT_COMPLETED",
            "FAILED, COMPENSATING",
            "FAILED, COMPLETED",
            "COMPENSATING, PENDING",
            "COMPENSATING, INVENTORY_LOCKED",
            "COMPENSATING, PAYMENT_PENDING",
            "COMPENSATING, PAYMENT_COMPLETED",
            "COMPENSATING, COMPLETED"
    })
    void shouldRejectInvalidTransitions(OrderStatus from, OrderStatus to) {
        order.setStatus(from);
        assertThatThrownBy(() -> stateMachine.transition(order, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition order");
    }
}
