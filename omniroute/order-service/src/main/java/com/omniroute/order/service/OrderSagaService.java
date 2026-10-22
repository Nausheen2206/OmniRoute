package com.omniroute.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniroute.common.events.*;
import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import com.omniroute.order.domain.SagaLog;
import com.omniroute.order.messaging.OrderEventProducer;
import com.omniroute.order.repository.OrderRepository;
import com.omniroute.order.repository.SagaLogRepository;
import com.omniroute.order.metrics.OrderMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orderRepository;
    private final SagaLogRepository sagaLogRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventProducer producer;
    private final ObjectMapper objectMapper;
    private final OrderMetrics orderMetrics;

    public OrderSagaService(OrderRepository orderRepository,
                            SagaLogRepository sagaLogRepository,
                            OrderStateMachine stateMachine,
                            OrderEventProducer producer,
                            ObjectMapper objectMapper,
                            OrderMetrics orderMetrics) {
        this.orderRepository = orderRepository;
        this.sagaLogRepository = sagaLogRepository;
        this.stateMachine = stateMachine;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.orderMetrics = orderMetrics;
    }

    @Transactional
    public Order createOrder(String customerId, List<OrderItem> items, BigDecimal totalAmount) {
        String orderId = UUID.randomUUID().toString();
        MDC.put("orderId", orderId);
        try {
            String itemsJson;
            try {
                itemsJson = objectMapper.writeValueAsString(items);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize order items", e);
            }

            Order order = new Order(orderId, customerId, itemsJson, totalAmount, OrderStatus.PENDING);
            orderRepository.save(order);
            orderMetrics.recordOrderCreated();

            OrderCreated createdEvent = new OrderCreated(orderId, customerId, items, totalAmount, order.getWarehouseLat(), order.getWarehouseLon());
            sagaLogRepository.save(new SagaLog(orderId, "ORDER_CREATED", createdEvent.eventId()));

            log.info("Created order {} with status PENDING for customer {}", orderId, customerId);

            // Publish after persisting
            producer.publishOrderCreated(createdEvent);

            return order;
        } finally {
            MDC.remove("orderId");
        }
    }

    public boolean recordStep(String orderId, String step, String eventId) {
        if (sagaLogRepository.existsByEventId(eventId)) {
            log.warn("Event {} already processed for order {}, skipping", eventId, orderId);
            return false;
        }
        try {
            sagaLogRepository.save(new SagaLog(orderId, step, eventId));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event {} detected via constraint for order {}, skipping", eventId, orderId);
            return false;
        }
    }

    @Transactional
    public void handleInventoryLocked(InventoryLocked event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "INVENTORY_LOCKED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) {
                log.warn("Order {} not found for InventoryLocked event", event.orderId());
                return;
            }

            stateMachine.transition(order, OrderStatus.INVENTORY_LOCKED);
            orderRepository.save(order);

            PaymentRequested paymentCmd = new PaymentRequested(order.getId(), order.getTotalAmount());
            try {
                producer.publishPaymentRequested(paymentCmd);
            } catch (OrderEventProducer.PaymentCircuitBreakerOpenException e) {
                log.warn("Circuit breaker fallback activated for order {}. Moving to PAYMENT_PENDING.", order.getId());
                stateMachine.transition(order, OrderStatus.PAYMENT_PENDING);
                orderRepository.save(order);
            }
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void handleInventoryLockFailed(InventoryLockFailed event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "INVENTORY_LOCK_FAILED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            order.setFailureReason(event.reason());
            stateMachine.transition(order, OrderStatus.FAILED);
            orderRepository.save(order);
            orderMetrics.recordOrderFailed(event.reason());
            if (order.getCreatedAt() != null) {
                orderMetrics.recordSagaDuration(Duration.between(order.getCreatedAt(), Instant.now()));
            }
            log.info("Order {} failed due to inventory lock failure: {}", order.getId(), event.reason());
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompleted event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "PAYMENT_COMPLETED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            stateMachine.transition(order, OrderStatus.PAYMENT_COMPLETED);
            orderRepository.save(order);

            OrderAllocated allocatedEvent = new OrderAllocated(order.getId(), order.getWarehouseLat(), order.getWarehouseLon());
            producer.publishOrderAllocated(allocatedEvent);
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void handleTruckAssigned(TruckAssigned event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "TRUCK_ASSIGNED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            stateMachine.transition(order, OrderStatus.COMPLETED);
            orderRepository.save(order);
            orderMetrics.recordOrderCompleted();
            if (order.getCreatedAt() != null) {
                orderMetrics.recordSagaDuration(Duration.between(order.getCreatedAt(), Instant.now()));
            }
            log.info("Order {} completed with truck {} at distance {} km",
                    order.getId(), event.truckId(), event.distanceKm());
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailed event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "PAYMENT_FAILED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            order.setFailureReason(event.reason());
            stateMachine.transition(order, OrderStatus.COMPENSATING);
            orderRepository.save(order);

            log.info("Order {} payment failed ({}). Publishing ReleaseInventory.", order.getId(), event.reason());
            producer.publishReleaseInventory(new ReleaseInventory(order.getId()));
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void handleInventoryReleased(InventoryReleased event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "INVENTORY_RELEASED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            stateMachine.transition(order, OrderStatus.FAILED);
            if (order.getFailureReason() == null) {
                order.setFailureReason("Compensated: inventory released");
            }
            orderRepository.save(order);
            orderMetrics.recordOrderFailed(order.getFailureReason());
            if (order.getCreatedAt() != null) {
                orderMetrics.recordSagaDuration(Duration.between(order.getCreatedAt(), Instant.now()));
            }
            log.info("Order {} compensation completed: moved to FAILED", order.getId());
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void handlePaymentRefunded(PaymentRefunded event) {
        MDC.put("orderId", event.orderId());
        try {
            if (!recordStep(event.orderId(), "PAYMENT_REFUNDED", event.eventId())) return;

            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) return;

            log.info("Order {} refunded. Publishing ReleaseInventory to warehouse.", order.getId());
            producer.publishReleaseInventory(new ReleaseInventory(order.getId()));
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void compensateStuckOrder(Order order, String reason) {
        MDC.put("orderId", order.getId());
        try {
            log.warn("Triggering watchdog compensation for stuck order {} in status {}: {}",
                    order.getId(), order.getStatus(), reason);

            order.setFailureReason(reason);

            if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
                stateMachine.transition(order, OrderStatus.COMPENSATING);
                orderRepository.save(order);
                producer.publishRefundPayment(new RefundPayment(order.getId()));
            } else if (order.getStatus() == OrderStatus.INVENTORY_LOCKED || order.getStatus() == OrderStatus.PAYMENT_PENDING) {
                stateMachine.transition(order, OrderStatus.COMPENSATING);
                orderRepository.save(order);
                producer.publishReleaseInventory(new ReleaseInventory(order.getId()));
            }
        } finally {
            MDC.remove("orderId");
        }
    }
}
