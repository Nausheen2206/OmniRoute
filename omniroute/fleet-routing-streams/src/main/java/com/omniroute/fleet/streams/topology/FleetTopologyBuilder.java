package com.omniroute.fleet.streams.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.Topics;
import com.omniroute.common.events.TruckPositionUpdated;
import com.omniroute.fleet.streams.domain.Assignment;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.metrics.FleetStreamsMetrics;
import com.omniroute.fleet.streams.serdes.CustomSerdes;
import com.omniroute.fleet.streams.spatial.GridIndex;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class FleetTopologyBuilder {

    public static final String TRUCKS_STORE = "trucks-state-store";
    public static final String CELL_INDEX_STORE = "cell-index-store";
    public static final String CELL_WINDOW_STORE = "cell-window-store";

    private final ObjectMapper objectMapper;
    private final FleetStreamsMetrics metrics;
    private final List<Assignment> recentAssignments;

    @Value("${fleet.stale-timeout-seconds:30}")
    private long staleTimeoutSeconds = 30;

    @Value("${fleet.busy-duration-seconds:60}")
    private long busyDurationSeconds = 60;

    @Value("${fleet.max-rings:10}")
    private int maxRings = 10;

    @Value("${fleet.window-seconds:30}")
    private long windowSeconds = 30;

    public FleetTopologyBuilder(
            ObjectMapper objectMapper,
            FleetStreamsMetrics metrics,
            List<Assignment> recentAssignments
    ) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.recentAssignments = recentAssignments;
    }

    public Topology buildTopology(StreamsBuilder builder) {
        // 1. Define State Stores
        StoreBuilder<KeyValueStore<String, TruckState>> trucksStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(TRUCKS_STORE),
                Serdes.String(),
                CustomSerdes.truckStateSerde(objectMapper)
        );
        builder.addStateStore(trucksStoreBuilder);

        StoreBuilder<KeyValueStore<String, String>> cellIndexStoreBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(CELL_INDEX_STORE),
                Serdes.String(),
                Serdes.String()
        );
        builder.addStateStore(cellIndexStoreBuilder);

        // 2. Consume FLEET_GPS
        KStream<String, OmniRouteEvent> gpsRaw = builder.stream(
                Topics.FLEET_GPS,
                Consumed.with(Serdes.String(), CustomSerdes.omniRouteEventSerde(objectMapper))
        );

        KStream<String, TruckPositionUpdated> validGps = gpsRaw
                .filter((k, v) -> v instanceof TruckPositionUpdated gps && GridIndex.isValidCoordinate(gps.lat(), gps.lon()))
                .mapValues((k, v) -> (TruckPositionUpdated) v);

        // Process GPS positions to update trucksStore and cellIndexStore
        validGps.process(
                () -> new GpsPositionProcessor(TRUCKS_STORE, CELL_INDEX_STORE),
                Named.as("gps-position-processor"),
                TRUCKS_STORE, CELL_INDEX_STORE
        );

        // 3. Windowed Aggregation (30-second tumbling window per spatial grid cell)
        validGps.selectKey((k, gps) -> GridIndex.getCellId(gps.lat(), gps.lon()))
                .groupByKey(Grouped.with(Serdes.String(), CustomSerdes.truckPositionSerde(objectMapper)))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(windowSeconds)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(CELL_WINDOW_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()));

        // 4. Consume ORDER_EVENTS and route OrderAllocated
        KStream<String, OmniRouteEvent> orderRaw = builder.stream(
                Topics.ORDER_EVENTS,
                Consumed.with(Serdes.String(), CustomSerdes.omniRouteEventSerde(objectMapper))
        );

        KStream<String, OmniRouteEvent> orderAllocatedStream = orderRaw
                .filter((k, v) -> v instanceof OrderAllocated);

        orderAllocatedStream.process(
                () -> new OrderAllocatedProcessor(
                        TRUCKS_STORE,
                        CELL_INDEX_STORE,
                        metrics,
                        recentAssignments,
                        staleTimeoutSeconds,
                        busyDurationSeconds,
                        maxRings
                ),
                Named.as("order-allocated-processor"),
                TRUCKS_STORE, CELL_INDEX_STORE
        ).to(
                Topics.ROUTING_COMMANDS,
                Produced.with(Serdes.String(), CustomSerdes.omniRouteEventSerde(objectMapper))
        );

        return builder.build();
    }
}
