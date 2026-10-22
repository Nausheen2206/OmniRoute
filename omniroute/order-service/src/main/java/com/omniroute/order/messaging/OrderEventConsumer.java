package com.omniroute.order.messaging;

import com.omniroute.common.events.*;
import com.omniroute.order.service.OrderSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderSagaService sagaService;

    public OrderEventConsumer(OrderSagaService sagaService) {
        this.sagaService = sagaService;
    }

    @KafkaListener(topics = Topics.WAREHOUSE_EVENTS, groupId = "order-service-warehouse-group")
    public void handleWarehouseEvents(OmniRouteEvent event) {
        if (event == null) return;
        MDC.put("orderId", event.orderId());
        try {
            log.info("Received warehouse event: {} for order {}", event.getClass().getSimpleName(), event.orderId());
            if (event instanceof InventoryLocked locked) {
                sagaService.handleInventoryLocked(locked);
            } else if (event instanceof InventoryLockFailed failed) {
                sagaService.handleInventoryLockFailed(failed);
            } else if (event instanceof InventoryReleased released) {
                sagaService.handleInventoryReleased(released);
            } else {
                log.warn("Unrecognized event type on warehouse-events: {}", event.getClass());
            }
        } finally {
            MDC.remove("orderId");
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "order-service-payment-group")
    public void handlePaymentEvents(OmniRouteEvent event) {
        if (event == null) return;
        MDC.put("orderId", event.orderId());
        try {
            log.info("Received payment event: {} for order {}", event.getClass().getSimpleName(), event.orderId());
            if (event instanceof PaymentCompleted completed) {
                sagaService.handlePaymentCompleted(completed);
            } else if (event instanceof PaymentFailed failed) {
                sagaService.handlePaymentFailed(failed);
            } else if (event instanceof PaymentRefunded refunded) {
                sagaService.handlePaymentRefunded(refunded);
            } else {
                log.warn("Unrecognized event type on payment-events: {}", event.getClass());
            }
        } finally {
            MDC.remove("orderId");
        }
    }

    @KafkaListener(topics = Topics.ROUTING_COMMANDS, groupId = "order-service-routing-group")
    public void handleRoutingCommands(OmniRouteEvent event) {
        if (event == null) return;
        MDC.put("orderId", event.orderId());
        try {
            log.info("Received routing command event: {} for order {}", event.getClass().getSimpleName(), event.orderId());
            if (event instanceof TruckAssigned assigned) {
                sagaService.handleTruckAssigned(assigned);
            } else {
                log.warn("Unrecognized event type on routing-commands: {}", event.getClass());
            }
        } finally {
            MDC.remove("orderId");
        }
    }
}
