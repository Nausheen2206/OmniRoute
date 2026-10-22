package com.omniroute.fleet.metrics;

import com.omniroute.fleet.service.FleetRoutingEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class FleetMetrics {

    private final Counter assignmentsCounter;
    private final Timer routingLatencyTimer;

    public FleetMetrics(MeterRegistry meterRegistry, FleetRoutingEngine routingEngine) {
        this.assignmentsCounter = Counter.builder("omniroute.fleet.assignments.made")
                .description("Total number of vehicle routing assignments made")
                .register(meterRegistry);

        this.routingLatencyTimer = Timer.builder("omniroute.fleet.routing.latency")
                .description("Latency of nearest-truck haversine search calculations")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        Gauge.builder("omniroute.fleet.trucks.tracked", routingEngine, engine -> (double) engine.getAllTrucks().size())
                .description("Current number of active trucks being tracked")
                .register(meterRegistry);
    }

    public void recordAssignment() {
        assignmentsCounter.increment();
    }

    public <T> T recordRoutingTime(Supplier<T> action) {
        return routingLatencyTimer.record(action);
    }
}
