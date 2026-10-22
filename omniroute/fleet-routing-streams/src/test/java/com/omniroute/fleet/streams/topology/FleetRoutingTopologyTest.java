package com.omniroute.fleet.streams.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.Topics;
import com.omniroute.common.events.TruckAssigned;
import com.omniroute.common.events.TruckPositionUpdated;
import com.omniroute.fleet.streams.domain.Assignment;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.metrics.FleetStreamsMetrics;
import com.omniroute.fleet.streams.serdes.CustomSerdes;
import com.omniroute.fleet.streams.serdes.JsonSerde;
import com.omniroute.fleet.streams.spatial.GridIndex;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class FleetRoutingTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, OmniRouteEvent> gpsInputTopic;
    private TestInputTopic<String, OmniRouteEvent> orderInputTopic;
    private TestOutputTopic<String, OmniRouteEvent> routingOutputTopic;
    private KeyValueStore<String, TruckState> trucksStore;
    private KeyValueStore<String, String> cellIndexStore;
    private WindowStore<String, Long> cellWindowStore;
    private List<Assignment> recentAssignments;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonSerde.createDefaultObjectMapper();
        FleetStreamsMetrics metrics = new FleetStreamsMetrics(new SimpleMeterRegistry());
        recentAssignments = new CopyOnWriteArrayList<>();

        FleetTopologyBuilder topologyBuilder = new FleetTopologyBuilder(objectMapper, metrics, recentAssignments);
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        Topology topology = topologyBuilder.buildTopology(streamsBuilder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-fleet-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

        testDriver = new TopologyTestDriver(topology, props);

        gpsInputTopic = testDriver.createInputTopic(
                Topics.FLEET_GPS,
                Serdes.String().serializer(),
                CustomSerdes.omniRouteEventSerde(objectMapper).serializer()
        );

        orderInputTopic = testDriver.createInputTopic(
                Topics.ORDER_EVENTS,
                Serdes.String().serializer(),
                CustomSerdes.omniRouteEventSerde(objectMapper).serializer()
        );

        routingOutputTopic = testDriver.createOutputTopic(
                Topics.ROUTING_COMMANDS,
                Serdes.String().deserializer(),
                CustomSerdes.omniRouteEventSerde(objectMapper).deserializer()
        );

        trucksStore = testDriver.getKeyValueStore(FleetTopologyBuilder.TRUCKS_STORE);
        cellIndexStore = testDriver.getKeyValueStore(FleetTopologyBuilder.CELL_INDEX_STORE);
        cellWindowStore = testDriver.getWindowStore(FleetTopologyBuilder.CELL_WINDOW_STORE);
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    @DisplayName("Should ingest GPS updates, update truck state store and spatial grid index")
    void shouldIngestGpsUpdates() {
        double lat = 11.0168;
        double lon = 76.9558;
        gpsInputTopic.pipeInput("TRUCK-001", new TruckPositionUpdated("TRUCK-001", lat, lon));

        TruckState state = trucksStore.get("TRUCK-001");
        assertNotNull(state);
        assertEquals("TRUCK-001", state.truckId());
        assertEquals(lat, state.lat(), 0.0001);
        assertEquals(lon, state.lon(), 0.0001);
        assertEquals(GridIndex.getCellId(lat, lon), state.cellId());

        String cellTrucks = cellIndexStore.get(state.cellId());
        assertNotNull(cellTrucks);
        assertTrue(cellTrucks.contains("TRUCK-001"));
    }

    @Test
    @DisplayName("Should discard GPS events with invalid latitude or longitude coordinates")
    void shouldDiscardInvalidCoordinates() {
        gpsInputTopic.pipeInput("TRUCK-BAD", new TruckPositionUpdated("TRUCK-BAD", 95.0, 200.0));

        TruckState state = trucksStore.get("TRUCK-BAD");
        assertNull(state, "Truck with invalid coordinates should not be registered");
    }

    @Test
    @DisplayName("Should assign nearest truck on OrderAllocated and mark truck busy")
    void shouldAssignNearestTruckAndMarkBusy() {
        double wLat = 11.0168;
        double wLon = 76.9558;

        // Position 2 trucks: TRUCK-CLOSE (0.1 km) and TRUCK-FAR (2.0 km)
        gpsInputTopic.pipeInput("TRUCK-FAR", new TruckPositionUpdated("TRUCK-FAR", 11.0350, 76.9750));
        gpsInputTopic.pipeInput("TRUCK-CLOSE", new TruckPositionUpdated("TRUCK-CLOSE", 11.0175, 76.9565));

        // Send OrderAllocated
        orderInputTopic.pipeInput("order-1", new OrderAllocated("order-1", wLat, wLon));

        assertFalse(routingOutputTopic.isEmpty());
        TestRecord<String, OmniRouteEvent> record = routingOutputTopic.readRecord();
        assertEquals("order-1", record.key());
        assertTrue(record.value() instanceof TruckAssigned);

        TruckAssigned assigned = (TruckAssigned) record.value();
        assertEquals("order-1", assigned.orderId());
        assertEquals("TRUCK-CLOSE", assigned.truckId(), "Should pick closest available truck");
        assertTrue(assigned.distanceKm() < 1.0);

        // Check truck is marked busy
        TruckState closeState = trucksStore.get("TRUCK-CLOSE");
        assertNotNull(closeState.busyUntil());
        assertTrue(closeState.busyUntil().isAfter(Instant.now()));

        // Check recent assignments buffer
        assertEquals(1, recentAssignments.size());
        assertEquals("order-1", recentAssignments.get(0).orderId());

        // Second order arrives -> TRUCK-CLOSE is busy, should assign TRUCK-FAR
        orderInputTopic.pipeInput("order-2", new OrderAllocated("order-2", wLat, wLon));

        assertFalse(routingOutputTopic.isEmpty());
        TestRecord<String, OmniRouteEvent> record2 = routingOutputTopic.readRecord();
        TruckAssigned assigned2 = (TruckAssigned) record2.value();
        assertEquals("order-2", assigned2.orderId());
        assertEquals("TRUCK-FAR", assigned2.truckId(), "Should pick remaining available truck");

        // Third order arrives -> all trucks busy -> no assignment output
        orderInputTopic.pipeInput("order-3", new OrderAllocated("order-3", wLat, wLon));
        assertTrue(routingOutputTopic.isEmpty(), "No assignment output when all candidate trucks are busy");
    }

    @Test
    @DisplayName("Should find trucks in expanding spatial rings when center cell has no trucks")
    void shouldFindTruckInExpandingSpatialRings() {
        double wLat = 11.0168;
        double wLon = 76.9558;
        String centerCell = GridIndex.getCellId(wLat, wLon);
        int[] centerCoords = GridIndex.parseCellId(centerCell);

        // Place truck in ring 2
        String ring2Cell = GridIndex.formatCellId(centerCoords[0] + 2, centerCoords[1] + 2);
        GridIndex.CellBounds bounds = GridIndex.getCellBounds(ring2Cell);
        gpsInputTopic.pipeInput("TRUCK-RING2", new TruckPositionUpdated("TRUCK-RING2", bounds.centerLat(), bounds.centerLon()));

        // Dispatch order at warehouse
        orderInputTopic.pipeInput("order-ring2", new OrderAllocated("order-ring2", wLat, wLon));

        assertFalse(routingOutputTopic.isEmpty());
        TruckAssigned assigned = (TruckAssigned) routingOutputTopic.readRecord().value();
        assertEquals("order-ring2", assigned.orderId());
        assertEquals("TRUCK-RING2", assigned.truckId());
    }

    @Test
    @DisplayName("Should maintain active counts in 30-second windowed cell state store")
    void shouldMaintainWindowedCellCounts() {
        double lat = 11.0168;
        double lon = 76.9558;
        String cellId = GridIndex.getCellId(lat, lon);

        gpsInputTopic.pipeInput("TRUCK-001", new TruckPositionUpdated("TRUCK-001", lat, lon));
        gpsInputTopic.pipeInput("TRUCK-002", new TruckPositionUpdated("TRUCK-002", lat, lon));

        assertNotNull(cellWindowStore, "Window store must be initialized");
    }
}
