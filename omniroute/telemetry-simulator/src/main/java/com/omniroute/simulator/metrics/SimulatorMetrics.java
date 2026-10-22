package com.omniroute.simulator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SimulatorMetrics {

    private final Counter eventsSentCounter;

    public SimulatorMetrics(MeterRegistry meterRegistry) {
        this.eventsSentCounter = Counter.builder("omniroute.simulator.events.sent")
                .description("Total number of truck GPS telemetry events published to Kafka")
                .register(meterRegistry);
    }

    public void recordEventSent() {
        eventsSentCounter.increment();
    }
}
