package com.omniroute.fleet.streams.topology;

import com.omniroute.common.events.TruckPositionUpdated;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.spatial.GridIndex;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

import java.time.Instant;

public class GpsPositionProcessor implements Processor<String, TruckPositionUpdated, Void, Void> {

    private final String trucksStoreName;
    private final String cellIndexStoreName;

    private KeyValueStore<String, TruckState> trucksStore;
    private KeyValueStore<String, String> cellIndexStore;

    public GpsPositionProcessor(String trucksStoreName, String cellIndexStoreName) {
        this.trucksStoreName = trucksStoreName;
        this.cellIndexStoreName = cellIndexStoreName;
    }

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        this.trucksStore = context.getStateStore(trucksStoreName);
        this.cellIndexStore = context.getStateStore(cellIndexStoreName);
    }

    @Override
    public void process(Record<String, TruckPositionUpdated> record) {
        TruckPositionUpdated gps = record.value();
        if (gps == null || gps.truckId() == null) {
            return;
        }

        if (!GridIndex.isValidCoordinate(gps.lat(), gps.lon())) {
            return;
        }

        String truckId = gps.truckId();
        TruckState existing = trucksStore.get(truckId);

        String oldCellId = existing != null ? existing.cellId() : null;
        String newCellId = GridIndex.getCellId(gps.lat(), gps.lon());

        Instant eventTime = gps.timestamp() != null ? gps.timestamp() : Instant.ofEpochMilli(record.timestamp());

        // Preserve active busy state if not yet expired
        Instant busyUntil = null;
        if (existing != null && existing.busyUntil() != null && existing.busyUntil().isAfter(eventTime)) {
            busyUntil = existing.busyUntil();
        }

        TruckState updated = new TruckState(truckId, gps.lat(), gps.lon(), eventTime, busyUntil, newCellId);
        trucksStore.put(truckId, updated);

        // Update spatial grid index
        if (oldCellId != null && !oldCellId.equals(newCellId)) {
            CellIndexHelper.removeTruck(cellIndexStore, oldCellId, truckId);
        }
        CellIndexHelper.addTruck(cellIndexStore, newCellId, truckId);
    }
}
