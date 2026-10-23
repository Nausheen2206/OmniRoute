package com.omniroute.order.messaging;

import com.omniroute.common.events.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.omniroute.order.client.PaymentServiceClient paymentServiceClient;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                              com.omniroute.order.client.PaymentServiceClient paymentServiceClient) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentServiceClient = paymentServiceClient;
    }

    public void publishOrderCreated(OrderCreated event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing OrderCreated to {} for orderId {}", Topics.ORDER_EVENTS, event.orderId());
            kafkaTemplate.send(Topics.ORDER_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentRequestedFallback")
    public void publishPaymentRequested(PaymentRequested event) {
        MDC.put("orderId", event.orderId());
        try {
            // Check availability of payment service; trips circuit breaker if payment service is down/stopped
            paymentServiceClient.checkAvailability();
            log.info("Publishing PaymentRequested to {} for orderId {}, amount {}",
                    Topics.PAYMENT_COMMANDS, event.orderId(), event.amount());
            kafkaTemplate.send(Topics.PAYMENT_COMMANDS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void paymentRequestedFallback(PaymentRequested event, Throwable throwable) {
        MDC.put("orderId", event.orderId());
        try {
            log.warn("Circuit breaker open or failed when publishing PaymentRequested for order {}: {}. Will fallback to PAYMENT_PENDING.",
                    event.orderId(), throwable.getMessage());
            // Fallback action is triggered in saga service via exception or status check
            throw new PaymentCircuitBreakerOpenException("Payment service circuit breaker open/failed: " + throwable.getMessage(), throwable);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishOrderAllocated(OrderAllocated event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing OrderAllocated to {} for orderId {}", Topics.ORDER_EVENTS, event.orderId());
            kafkaTemplate.send(Topics.ORDER_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishReleaseInventory(ReleaseInventory event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing ReleaseInventory to {} for orderId {}", Topics.WAREHOUSE_COMMANDS, event.orderId());
            kafkaTemplate.send(Topics.WAREHOUSE_COMMANDS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishRefundPayment(RefundPayment event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing RefundPayment to {} for orderId {}", Topics.PAYMENT_COMMANDS, event.orderId());
            kafkaTemplate.send(Topics.PAYMENT_COMMANDS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public static class PaymentCircuitBreakerOpenException extends RuntimeException {
        public PaymentCircuitBreakerOpenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
