package com.omniroute.order.service;

import com.omniroute.common.events.PaymentRequested;
import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import com.omniroute.order.messaging.OrderEventProducer;
import com.omniroute.order.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentPendingRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentPendingRetryScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderSagaService sagaService;
    private final OrderEventProducer producer;
    private final CircuitBreaker circuitBreaker;

    @Value("${payment.retry-window-seconds:60}")
    private long retryWindowSeconds;

    public PaymentPendingRetryScheduler(OrderRepository orderRepository,
                                       OrderSagaService sagaService,
                                       OrderEventProducer producer,
                                       CircuitBreakerRegistry circuitBreakerRegistry) {
        this.orderRepository = orderRepository;
        this.sagaService = sagaService;
        this.producer = producer;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("paymentService");
    }

    @Scheduled(fixedDelayString = "${payment.retry-interval-ms:2000}")
    public void retryPaymentPendingOrders() {
        List<Order> pendingOrders = orderRepository.findByStatus(OrderStatus.PAYMENT_PENDING);
        if (pendingOrders.isEmpty()) {
            return;
        }

        CircuitBreaker.State cbState = circuitBreaker.getState();
        log.info("Checking {} orders in PAYMENT_PENDING. Circuit breaker state: {}", pendingOrders.size(), cbState);

        Instant now = Instant.now();

        for (Order order : pendingOrders) {
            Instant referenceTime = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
            long elapsedSeconds = Duration.between(referenceTime, now).toSeconds();

            if (elapsedSeconds >= retryWindowSeconds) {
                log.warn("Order {} has exceeded retry window in PAYMENT_PENDING ({}s >= {}s). Compensating saga.",
                        order.getId(), elapsedSeconds, retryWindowSeconds);
                try {
                    sagaService.compensateStuckOrder(order, "payment unavailable");
                } catch (Exception e) {
                    log.error("Failed to compensate timed-out PAYMENT_PENDING order {}: {}", order.getId(), e.getMessage());
                }
                continue;
            }

            if (cbState == CircuitBreaker.State.CLOSED || cbState == CircuitBreaker.State.HALF_OPEN) {
                log.info("Re-attempting PaymentRequested for queued order {}", order.getId());
                try {
                    PaymentRequested cmd = new PaymentRequested(order.getId(), order.getTotalAmount());
                    producer.publishPaymentRequested(cmd);
                    log.info("Successfully re-dispatched PaymentRequested for queued order {}", order.getId());
                } catch (Exception e) {
                    log.warn("Payment re-dispatch failed for order {}: {}", order.getId(), e.getMessage());
                    break;
                }
            }
        }
    }
}
