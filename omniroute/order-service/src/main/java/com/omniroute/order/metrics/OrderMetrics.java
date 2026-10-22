package com.omniroute.order.metrics;

import com.omniroute.order.domain.OrderStatus;
import com.omniroute.order.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OrderMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter ordersCreatedCounter;
    private final Counter ordersCompletedCounter;
    private final Timer sagaDurationTimer;

    public OrderMetrics(MeterRegistry meterRegistry, OrderRepository orderRepository) {
        this.meterRegistry = meterRegistry;

        this.ordersCreatedCounter = Counter.builder("omniroute.orders.created")
                .description("Total number of orders created")
                .register(meterRegistry);

        this.ordersCompletedCounter = Counter.builder("omniroute.orders.completed")
                .description("Total number of orders successfully completed")
                .register(meterRegistry);

        this.sagaDurationTimer = Timer.builder("omniroute.orders.saga.duration")
                .description("End-to-end duration of order Saga executions")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // Register active orders per status gauge
        for (OrderStatus status : OrderStatus.values()) {
            Gauge.builder("omniroute.orders.active", orderRepository, repo -> (double) repo.countByStatus(status))
                    .tag("status", status.name())
                    .description("Current number of orders in status: " + status.name())
                    .register(meterRegistry);
        }
    }

    public void recordOrderCreated() {
        ordersCreatedCounter.increment();
    }

    public void recordOrderCompleted() {
        ordersCompletedCounter.increment();
    }

    public void recordOrderFailed(String reason) {
        String sanitizedReason = (reason != null && !reason.isBlank()) ? reason : "Unknown";
        // Keep tag cardinality manageable by truncating or standardizing
        if (sanitizedReason.contains("13.13")) {
            sanitizedReason = "SimulatedPaymentFailure";
        } else if (sanitizedReason.contains("declined") || sanitizedReason.contains("Payment failed")) {
            sanitizedReason = "PaymentDeclined";
        } else if (sanitizedReason.contains("Insufficient stock")) {
            sanitizedReason = "InsufficientStock";
        } else if (sanitizedReason.contains("timeout") || sanitizedReason.contains("Watchdog")) {
            sanitizedReason = "WatchdogTimeout";
        } else if (sanitizedReason.length() > 30) {
            sanitizedReason = sanitizedReason.substring(0, 30);
        }

        Counter.builder("omniroute.orders.failed")
                .description("Total number of failed orders tagged by reason")
                .tag("reason", sanitizedReason)
                .register(meterRegistry)
                .increment();
    }

    public void recordSagaDuration(Duration duration) {
        if (duration != null && !duration.isNegative()) {
            sagaDurationTimer.record(duration);
        }
    }
}
