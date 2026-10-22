package com.omniroute.order;

import com.omniroute.common.events.*;
import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import com.omniroute.order.domain.SagaLog;
import com.omniroute.order.messaging.OrderEventProducer;
import com.omniroute.order.repository.OrderRepository;
import com.omniroute.order.repository.SagaLogRepository;
import com.omniroute.order.service.OrderSagaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class OrderSagaIntegrationTest {

    @Autowired
    private OrderSagaService sagaService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SagaLogRepository sagaLogRepository;

    @MockBean
    private OrderEventProducer producer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        sagaLogRepository.deleteAll();
        reset(producer);
    }

    @Test
    @DisplayName("Requirement 1: Successful order reaches COMPLETED through full Saga lifecycle")
    void testSuccessfulOrderReachesCompleted() {
        // Step 1: Create Order
        Order order = sagaService.createOrder(
                "CUST-001",
                List.of(new OrderItem("SKU-001", 2)),
                BigDecimal.valueOf(150.00)
        );
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(producer).publishOrderCreated(any(OrderCreated.class));

        // Step 2: Inventory Locked
        sagaService.handleInventoryLocked(new InventoryLocked(order.getId()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.INVENTORY_LOCKED);
        });
        verify(producer).publishPaymentRequested(any(PaymentRequested.class));

        // Step 3: Payment Completed
        sagaService.handlePaymentCompleted(new PaymentCompleted(order.getId()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
        });
        verify(producer).publishOrderAllocated(any(OrderAllocated.class));

        // Step 4: Truck Assigned -> COMPLETED
        sagaService.handleTruckAssigned(new TruckAssigned(order.getId(), "TRUCK-042", 3.25));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        });

        // Verify full Saga audit log
        List<SagaLog> logs = sagaLogRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        assertThat(logs).extracting(SagaLog::getStep).containsExactly(
                "ORDER_CREATED",
                "INVENTORY_LOCKED",
                "PAYMENT_COMPLETED",
                "TRUCK_ASSIGNED"
        );
    }

    @Test
    @DisplayName("Requirement 2: Forced payment failure (total 13.13) triggers compensation and ends FAILED")
    void testForcedPaymentFailureEndsFailed() {
        Order order = sagaService.createOrder(
                "CUST-FAIL",
                List.of(new OrderItem("SKU-002", 1)),
                BigDecimal.valueOf(13.13)
        );

        // Inventory locks first
        sagaService.handleInventoryLocked(new InventoryLocked(order.getId()));

        // Simulated payment failure
        sagaService.handlePaymentFailed(new PaymentFailed(order.getId(), "Simulated payment failure (amount 13.13)"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
        });
        verify(producer).publishReleaseInventory(any(ReleaseInventory.class));

        // Inventory released confirmation from warehouse
        sagaService.handleInventoryReleased(new InventoryReleased(order.getId()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(o.getFailureReason()).contains("13.13");
        });
    }

    @Test
    @DisplayName("Requirement 3: Duplicate ReleaseInventory / event messages processed idempotently only once")
    void testDuplicateReleaseInventoryProcessedOnce() {
        Order order = sagaService.createOrder(
                "CUST-DUP",
                List.of(new OrderItem("SKU-001", 1)),
                BigDecimal.valueOf(50.00)
        );
        sagaService.handleInventoryLocked(new InventoryLocked(order.getId()));
        sagaService.handlePaymentFailed(new PaymentFailed(order.getId(), "Error"));

        String eventId = UUID.randomUUID().toString();
        InventoryReleased releaseEvent = new InventoryReleased(eventId, order.getId(), Instant.now());

        // First message
        sagaService.handleInventoryReleased(releaseEvent);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.FAILED);
        });

        // Duplicate second message with same eventId
        sagaService.handleInventoryReleased(releaseEvent);

        // Verify saga log only contains single INVENTORY_RELEASED step
        List<SagaLog> logs = sagaLogRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
        long releaseSteps = logs.stream().filter(l -> "INVENTORY_RELEASED".equals(l.getStep())).count();
        assertThat(releaseSteps).isEqualTo(1);
    }

    @Test
    @DisplayName("Requirement 4: Insufficient stock ends FAILED without any payment attempt")
    void testInsufficientStockEndsFailedWithoutPaymentAttempt() {
        Order order = sagaService.createOrder(
                "CUST-STOCK",
                List.of(new OrderItem("SKU-999", 9999)),
                BigDecimal.valueOf(200.00)
        );

        // Warehouse returns InventoryLockFailed
        sagaService.handleInventoryLockFailed(new InventoryLockFailed(order.getId(), "Insufficient stock for SKU-999"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(o.getFailureReason()).contains("Insufficient stock");
        });

        // Zero calls to payment service
        verify(producer, never()).publishPaymentRequested(any());
    }

    @Test
    @DisplayName("Requirement 5: Open circuit breaker produces PAYMENT_PENDING fallback")
    void testOpenCircuitBreakerProducesPaymentPendingFallback() {
        Order order = sagaService.createOrder(
                "CUST-CB",
                List.of(new OrderItem("SKU-001", 1)),
                BigDecimal.valueOf(80.00)
        );

        // Mock circuit breaker opening when publishPaymentRequested is called
        doThrow(new OrderEventProducer.PaymentCircuitBreakerOpenException("Circuit breaker open", new RuntimeException()))
                .when(producer).publishPaymentRequested(any());

        sagaService.handleInventoryLocked(new InventoryLocked(order.getId()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Order o = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        });
    }
}
