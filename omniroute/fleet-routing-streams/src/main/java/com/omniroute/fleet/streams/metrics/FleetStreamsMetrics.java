package com.omniroute.fleet.streams.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class FleetStreamsMetrics {

    private final Counter assignmentsCounter;
    private final Timer routingLatencyTimer;
    private final AtomicInteger trucksTrackedGauge = new AtomicInteger(0);
    private final AtomicInteger trucksAvailableGauge = new AtomicInteger(0);
    private final AtomicInteger activeCellsGauge = new AtomicInteger(0);

    public FleetStreamsMetrics(MeterRegistry meterRegistry) {
        this.assignmentsCounter = Counter.builder("omniroute.fleet.assignments.made")
                .description("Total number of vehicle routing assignments made by Kafka Streams")
                .register(meterRegistry);

        this.routingLatencyTimer = Timer.builder("omniroute.fleet.routing.latency")
                .description("Latency of nearest-truck spatial haversine search calculations")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        Gauge.builder("omniroute.fleet.trucks.tracked", trucksTrackedGauge, AtomicInteger::get)
                .description("Current number of active trucks being tracked by Kafka Streams")
                .register(meterRegistry);

        Gauge.builder("omniroute.fleet.trucks.available", trucksAvailableGauge, AtomicInteger::get)
                .description("Current number of idle/available trucks")
                .register(meterRegistry);

        Gauge.builder("omniroute.fleet.cells.active", activeCellsGauge, AtomicInteger::get)
                .description("Current number of active spatial grid cells containing trucks")
                .register(meterRegistry);
    }

    public void recordAssignment() {
        assignmentsCounter.increment();
    }

    public <T> T recordRoutingTime(Supplier<T> action) {
        return routingLatencyTimer.record(action);
    }

    public void updateGauges(int tracked, int available, int activeCells) {
        trucksTrackedGauge.set(tracked);
        trucksAvailableGauge.set(available);
        activeCellsGauge.set(activeCells);
    }
}
