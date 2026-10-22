package com.omniroute.order.service;

import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);

    public boolean transition(Order order, OrderStatus newStatus) {
        OrderStatus current = order.getStatus();
        if (current == newStatus) {
            log.info("Order {} is already in status {}", order.getId(), current);
            return true;
        }

        if (!current.canTransitionTo(newStatus)) {
            log.error("Illegal state transition for order {}: {} -> {}", order.getId(), current, newStatus);
            throw new IllegalStateException("Cannot transition order " + order.getId() + " from " + current + " to " + newStatus);
        }

        log.info("Transitioning order {}: {} -> {}", order.getId(), current, newStatus);
        order.setStatus(newStatus);
        return true;
    }
}
