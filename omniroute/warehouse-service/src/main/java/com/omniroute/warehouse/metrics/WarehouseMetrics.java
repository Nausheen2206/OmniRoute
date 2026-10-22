package com.omniroute.warehouse.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WarehouseMetrics {

    private final Counter reservationsCreatedCounter;
    private final Counter reservationsReleasedCounter;

    public WarehouseMetrics(MeterRegistry meterRegistry) {
        this.reservationsCreatedCounter = Counter.builder("omniroute.warehouse.reservations.created")
                .description("Total number of inventory reservations created")
                .register(meterRegistry);

        this.reservationsReleasedCounter = Counter.builder("omniroute.warehouse.reservations.released")
                .description("Total number of inventory reservations released (compensated)")
                .register(meterRegistry);
    }

    public void recordReservationCreated() {
        reservationsCreatedCounter.increment();
    }

    public void recordReservationReleased() {
        reservationsReleasedCounter.increment();
    }
}
