package com.omniroute.fleet.streams.service;

import com.omniroute.fleet.streams.domain.Assignment;
import com.omniroute.fleet.streams.domain.CellSummary;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.metrics.FleetStreamsMetrics;
import com.omniroute.fleet.streams.spatial.GridIndex;
import com.omniroute.fleet.streams.topology.CellIndexHelper;
import com.omniroute.fleet.streams.topology.FleetTopologyBuilder;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class FleetStreamsQueryService {

    private static final Logger log = LoggerFactory.getLogger(FleetStreamsQueryService.class);

    private final StreamsBuilderFactoryBean streamsFactoryBean;
    private final FleetStreamsMetrics metrics;
    private final List<Assignment> recentAssignments;

    @Value("${fleet.stale-timeout-seconds:30}")
    private long staleTimeoutSeconds = 30;

    @Value("${fleet.max-returned-trucks:300}")
    private int maxReturnedTrucks = 300;

    @Value("${fleet.hub-lat:11.0168}")
    private double hubLat = 11.0168;

    @Value("${fleet.hub-lon:76.9558}")
    private double hubLon = 76.9558;

    public FleetStreamsQueryService(
            @Autowired(required = false) StreamsBuilderFactoryBean streamsFactoryBean,
            FleetStreamsMetrics metrics,
            List<Assignment> recentAssignments
    ) {
        this.streamsFactoryBean = streamsFactoryBean;
        this.metrics = metrics;
        this.recentAssignments = recentAssignments;
    }

    public Collection<TruckState> getAllTrucks() {
        ReadOnlyKeyValueStore<String, TruckState> store = getTrucksStore();
        if (store == null) {
            return Collections.emptyList();
        }

        List<TruckState> list = new ArrayList<>();
        try (KeyValueIterator<String, TruckState> it = store.all()) {
            while (it.hasNext()) {
                TruckState state = it.next().value;
                if (state != null) {
                    list.add(state);
                }
            }
        } catch (Exception e) {
            log.warn("Error querying trucks state store: {}", e.getMessage());
        }

        if (list.size() <= maxReturnedTrucks) {
            return Collections.unmodifiableCollection(list);
        }

        Instant now = Instant.now();
        List<TruckState> assignedTrucks = new ArrayList<>();
        List<TruckState> availableTrucks = new ArrayList<>();

        for (TruckState truck : list) {
            if (truck.busyUntil() != null && truck.busyUntil().isAfter(now)) {
                assignedTrucks.add(truck);
            } else {
                availableTrucks.add(truck);
            }
        }

        availableTrucks.sort(Comparator.comparingDouble(t ->
                GridIndex.haversineDistance(hubLat, hubLon, t.lat(), t.lon())));

        int remainingSlots = Math.max(0, maxReturnedTrucks - assignedTrucks.size());
        List<TruckState> result = new ArrayList<>(assignedTrucks);
        result.addAll(availableTrucks.subList(0, Math.min(remainingSlots, availableTrucks.size())));

        return Collections.unmodifiableCollection(result);
    }

    public List<Assignment> getRecentAssignments() {
        return Collections.unmodifiableList(recentAssignments);
    }

    public List<CellSummary> getActiveCells() {
        ReadOnlyKeyValueStore<String, String> cellStore = getCellIndexStore();
        ReadOnlyKeyValueStore<String, TruckState> truckStore = getTrucksStore();
        if (cellStore == null) {
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        List<CellSummary> summaries = new ArrayList<>();

        try (KeyValueIterator<String, String> it = cellStore.all()) {
            while (it.hasNext()) {
                var entry = it.next();
                String cellId = entry.key;
                Set<String> truckIds = CellIndexHelper.getTrucks(cellStore, cellId);
                int activeCount = 0;

                if (truckStore != null) {
                    for (String tId : truckIds) {
                        TruckState ts = truckStore.get(tId);
                        if (ts != null && ts.isAvailable(now, staleTimeoutSeconds)) {
                            activeCount++;
                        }
                    }
                }

                GridIndex.CellBounds bounds = GridIndex.getCellBounds(cellId);
                summaries.add(new CellSummary(
                        cellId,
                        bounds,
                        truckIds.size(),
                        activeCount,
                        new ArrayList<>(truckIds),
                        0L
                ));
            }
        } catch (Exception e) {
            log.warn("Error querying cell index state store: {}", e.getMessage());
        }

        return Collections.unmodifiableList(summaries);
    }

    @Scheduled(fixedRate = 2000)
    public void refreshGauges() {
        ReadOnlyKeyValueStore<String, TruckState> store = getTrucksStore();
        ReadOnlyKeyValueStore<String, String> cellStore = getCellIndexStore();
        if (store == null) return;

        Instant now = Instant.now();
        int tracked = 0;
        int available = 0;

        try (KeyValueIterator<String, TruckState> it = store.all()) {
            while (it.hasNext()) {
                TruckState ts = it.next().value;
                if (ts != null) {
                    tracked++;
                    if (ts.isAvailable(now, staleTimeoutSeconds)) {
                        available++;
                    }
                }
            }
        } catch (Exception ignored) {}

        int activeCells = 0;
        if (cellStore != null) {
            try (KeyValueIterator<String, String> it = cellStore.all()) {
                while (it.hasNext()) {
                    it.next();
                    activeCells++;
                }
            } catch (Exception ignored) {}
        }

        metrics.updateGauges(tracked, available, activeCells);
    }

    private ReadOnlyKeyValueStore<String, TruckState> getTrucksStore() {
        if (streamsFactoryBean == null) return null;
        KafkaStreams streams = streamsFactoryBean.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            return null;
        }
        try {
            return streams.store(StoreQueryParameters.fromNameAndType(
                    FleetTopologyBuilder.TRUCKS_STORE,
                    QueryableStoreTypes.keyValueStore()
            ));
        } catch (InvalidStateStoreException e) {
            return null;
        }
    }

    private ReadOnlyKeyValueStore<String, String> getCellIndexStore() {
        if (streamsFactoryBean == null) return null;
        KafkaStreams streams = streamsFactoryBean.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            return null;
        }
        try {
            return streams.store(StoreQueryParameters.fromNameAndType(
                    FleetTopologyBuilder.CELL_INDEX_STORE,
                    QueryableStoreTypes.keyValueStore()
            ));
        } catch (InvalidStateStoreException e) {
            return null;
        }
    }
}
