package com.omniroute.simulator.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorMetricsTest {

    private MeterRegistry meterRegistry;
    private SimulatorMetrics simulatorMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        simulatorMetrics = new SimulatorMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should increment eventsSent counter when recordEventSent is invoked")
    void shouldIncrementEventsSentCounter() {
        simulatorMetrics.recordEventSent();
        simulatorMetrics.recordEventSent();
        simulatorMetrics.recordEventSent();

        var counter = meterRegistry.find("omniroute.simulator.events.sent").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should compute positive throughput eps on computeThroughput()")
    void shouldComputeThroughput() throws InterruptedException {
        // Record 100 events
        for (int i = 0; i < 100; i++) {
            simulatorMetrics.recordEventSent();
        }

        Thread.sleep(50);
        simulatorMetrics.computeThroughput();

        var gauge = meterRegistry.find("omniroute.simulator.events.per.second").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isGreaterThan(0.0);
    }
}
