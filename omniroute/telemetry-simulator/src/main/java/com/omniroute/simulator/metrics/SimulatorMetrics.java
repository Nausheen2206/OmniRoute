package com.omniroute.simulator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SimulatorMetrics {

    private final Counter eventsSentCounter;
    private final AtomicLong totalEventsSent = new AtomicLong(0);
    private final AtomicLong lastSampleCount = new AtomicLong(0);
    private final AtomicLong lastSampleTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicReference<Double> currentThroughput = new AtomicReference<>(0.0);

    public SimulatorMetrics(MeterRegistry meterRegistry) {
        this.eventsSentCounter = Counter.builder("omniroute.simulator.events.sent")
                .description("Total number of truck GPS telemetry events published to Kafka")
                .register(meterRegistry);

        Gauge.builder("omniroute.simulator.events.per.second", currentThroughput, AtomicReference::get)
                .description("Instantaneous GPS telemetry events published per second")
                .register(meterRegistry);
    }

    public void recordEventSent() {
        eventsSentCounter.increment();
        totalEventsSent.incrementAndGet();
    }

    @Scheduled(fixedRate = 1000)
    public void computeThroughput() {
        long now = System.currentTimeMillis();
        long currentTotal = totalEventsSent.get();
        long elapsedMs = now - lastSampleTime.getAndSet(now);
        long eventsDelta = currentTotal - lastSampleCount.getAndSet(currentTotal);

        if (elapsedMs > 0) {
            double eps = (eventsDelta * 1000.0) / elapsedMs;
            currentThroughput.set(Math.round(eps * 100.0) / 100.0);
        }
    }
}
