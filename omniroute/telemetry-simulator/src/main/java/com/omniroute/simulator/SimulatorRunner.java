package com.omniroute.simulator;

import com.omniroute.common.events.Topics;
import com.omniroute.common.events.TruckPositionUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SimulatorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorRunner.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.omniroute.simulator.metrics.SimulatorMetrics simulatorMetrics;

    @Value("${simulator.truck-count:200}")
    private int truckCount;

    @Value("${simulator.interval-ms:500}")
    private long intervalMs;

    @Value("${simulator.center-lat:11.0168}")
    private double centerLat;

    @Value("${simulator.center-lon:76.9558}")
    private double centerLon;

    @Value("${simulator.radius-degrees:0.15}")
    private double radiusDegrees;

    public SimulatorRunner(KafkaTemplate<String, Object> kafkaTemplate,
                           com.omniroute.simulator.metrics.SimulatorMetrics simulatorMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.simulatorMetrics = simulatorMetrics;
    }

    @Override
    public void run(String... args) {
        log.info("Starting Telemetry Simulator with {} trucks on virtual threads (emit interval: {}ms)...", truckCount, intervalMs);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 1; i <= truckCount; i++) {
            String truckId = truckCount > 999 ? String.format("TRUCK-%05d", i) : String.format("TRUCK-%03d", i);
            executor.submit(() -> runTruckSimulation(truckId));
        }
    }

    private void runTruckSimulation(String truckId) {
        Random random = new Random();
        // Initial random position within radius
        double currentLat = centerLat + (random.nextDouble() * 2 - 1) * radiusDegrees;
        double currentLon = centerLon + (random.nextDouble() * 2 - 1) * radiusDegrees;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Random walk step
                double deltaLat = (random.nextDouble() - 0.5) * 0.001;
                double deltaLon = (random.nextDouble() - 0.5) * 0.001;

                currentLat += deltaLat;
                currentLon += deltaLon;

                // Keep bounded within radius
                if (Math.abs(currentLat - centerLat) > radiusDegrees) {
                    currentLat = centerLat;
                }
                if (Math.abs(currentLon - centerLon) > radiusDegrees) {
                    currentLon = centerLon;
                }

                TruckPositionUpdated event = new TruckPositionUpdated(truckId, currentLat, currentLon);
                kafkaTemplate.send(Topics.FLEET_GPS, truckId, event);
                simulatorMetrics.recordEventSent();

                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error publishing GPS for truck {}: {}", truckId, e.getMessage());
            }
        }
    }
}
