package com.omniroute.fleet.streams.topology;

import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.TruckAssigned;
import com.omniroute.fleet.streams.domain.Assignment;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.metrics.FleetStreamsMetrics;
import com.omniroute.fleet.streams.spatial.GridIndex;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public class OrderAllocatedProcessor implements Processor<String, OmniRouteEvent, String, OmniRouteEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderAllocatedProcessor.class);

    private final String trucksStoreName;
    private final String cellIndexStoreName;
    private final FleetStreamsMetrics metrics;
    private final List<Assignment> recentAssignments;
    private final long staleTimeoutSeconds;
    private final long busyDurationSeconds;
    private final int maxRings;

    private ProcessorContext<String, OmniRouteEvent> context;
    private KeyValueStore<String, TruckState> trucksStore;
    private KeyValueStore<String, String> cellIndexStore;

    public OrderAllocatedProcessor(
            String trucksStoreName,
            String cellIndexStoreName,
            FleetStreamsMetrics metrics,
            List<Assignment> recentAssignments,
            long staleTimeoutSeconds,
            long busyDurationSeconds,
            int maxRings
    ) {
        this.trucksStoreName = trucksStoreName;
        this.cellIndexStoreName = cellIndexStoreName;
        this.metrics = metrics;
        this.recentAssignments = recentAssignments;
        this.staleTimeoutSeconds = staleTimeoutSeconds;
        this.busyDurationSeconds = busyDurationSeconds;
        this.maxRings = maxRings;
    }

    @Override
    public void init(ProcessorContext<String, OmniRouteEvent> context) {
        this.context = context;
        this.trucksStore = context.getStateStore(trucksStoreName);
        this.cellIndexStore = context.getStateStore(cellIndexStoreName);
    }

    @Override
    public void process(Record<String, OmniRouteEvent> record) {
        OmniRouteEvent event = record.value();
        if (!(event instanceof OrderAllocated allocated)) {
            return;
        }

        String orderId = allocated.orderId();
        MDC.put("orderId", orderId);
        long startTime = System.nanoTime();

        try {
            log.info("Kafka Streams: Received OrderAllocated for order {}. Searching nearest truck via spatial rings...", orderId);

            double warehouseLat = allocated.warehouseLat();
            double warehouseLon = allocated.warehouseLon();
            Instant now = Instant.now();

            TruckState bestTruck = null;
            double minDistance = Double.MAX_VALUE;

            String centerCellId = GridIndex.getCellId(warehouseLat, warehouseLon);

            // 1. Concentric ring expansion search
            for (int ring = 0; ring <= maxRings; ring++) {
                List<String> ringCells = GridIndex.getRingCells(centerCellId, ring);
                for (String cellId : ringCells) {
                    Set<String> truckIds = CellIndexHelper.getTrucks(cellIndexStore, cellId);
                    for (String truckId : truckIds) {
                        TruckState truck = trucksStore.get(truckId);
                        if (truck != null && truck.isAvailable(now, staleTimeoutSeconds)) {
                            double dist = GridIndex.haversineDistance(warehouseLat, warehouseLon, truck.lat(), truck.lon());
                            if (dist < minDistance) {
                                minDistance = dist;
                                bestTruck = truck;
                            }
                        }
                    }
                }
                // Pruning check: if nearest truck found is closer than next ring boundary
                if (bestTruck != null) {
                    double nextRingMinDistKm = ring * GridIndex.DEFAULT_CELL_SIZE * 110.0;
                    if (minDistance <= nextRingMinDistKm) {
                        break;
                    }
                }
            }

            // 2. Fallback scan if ring search didn't find any truck
            if (bestTruck == null) {
                try (KeyValueIterator<String, TruckState> it = trucksStore.all()) {
                    while (it.hasNext()) {
                        TruckState truck = it.next().value;
                        if (truck != null && truck.isAvailable(now, staleTimeoutSeconds)) {
                            double dist = GridIndex.haversineDistance(warehouseLat, warehouseLon, truck.lat(), truck.lon());
                            if (dist < minDistance) {
                                minDistance = dist;
                                bestTruck = truck;
                            }
                        }
                    }
                }
            }

            if (bestTruck != null) {
                // Mark truck busy
                Instant busyUntil = now.plusSeconds(busyDurationSeconds);
                TruckState updated = new TruckState(
                        bestTruck.truckId(),
                        bestTruck.lat(),
                        bestTruck.lon(),
                        bestTruck.lastUpdate(),
                        busyUntil,
                        bestTruck.cellId()
                );
                trucksStore.put(bestTruck.truckId(), updated);

                double roundedDistance = Math.round(minDistance * 100.0) / 100.0;
                TruckAssigned truckAssigned = new TruckAssigned(orderId, bestTruck.truckId(), roundedDistance);

                // Update recent assignments list
                if (recentAssignments != null) {
                    recentAssignments.add(0, new Assignment(orderId, bestTruck.truckId(), roundedDistance, now));
                    if (recentAssignments.size() > 100) {
                        recentAssignments.remove(recentAssignments.size() - 1);
                    }
                }

                if (metrics != null) {
                    metrics.recordAssignment();
                }

                log.info("Kafka Streams: Assigned truck {} to order {} (dist: {} km)",
                        bestTruck.truckId(), orderId, roundedDistance);

                context.forward(new Record<>(orderId, truckAssigned, record.timestamp()));
            } else {
                log.warn("Kafka Streams: No available truck for order {}. Watchdog will compensate.", orderId);
            }
        } finally {
            if (metrics != null) {
                long durationNs = System.nanoTime() - startTime;
                metrics.recordRoutingTime(() -> durationNs);
            }
            MDC.remove("orderId");
        }
    }
}
