package com.omniroute.fleet.streams.spatial;

import com.omniroute.fleet.streams.domain.TruckState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialNearestSelectionTest {

    @Test
    @DisplayName("Should select closest truck by Haversine distance within center cell")
    void shouldSelectClosestTruckInCenterCell() {
        double warehouseLat = 11.0168;
        double warehouseLon = 76.9558;
        Instant now = Instant.now();

        TruckState closer = new TruckState("T-CLOSE", 11.0170, 76.9560, now, null, GridIndex.getCellId(11.0170, 76.9560));
        TruckState farther = new TruckState("T-FAR", 11.0300, 76.9700, now, null, GridIndex.getCellId(11.0300, 76.9700));

        double distCloser = GridIndex.haversineDistance(warehouseLat, warehouseLon, closer.lat(), closer.lon());
        double distFarther = GridIndex.haversineDistance(warehouseLat, warehouseLon, farther.lat(), farther.lon());

        assertTrue(distCloser < distFarther);
        assertTrue(distCloser < 1.0, "Closer truck should be under 1 km away");
    }

    @Test
    @DisplayName("Should correctly filter out busy trucks and stale trucks")
    void shouldFilterBusyAndStaleTrucks() {
        Instant now = Instant.now();
        long staleTimeoutSeconds = 30;

        // Available truck
        TruckState available = new TruckState("T-AVAIL", 11.0168, 76.9558, now.minusSeconds(5), null);
        assertTrue(available.isAvailable(now, staleTimeoutSeconds));

        // Busy truck
        TruckState busy = new TruckState("T-BUSY", 11.0168, 76.9558, now.minusSeconds(5), now.plusSeconds(45));
        assertFalse(busy.isAvailable(now, staleTimeoutSeconds));

        // Stale truck (no GPS update for 40 seconds)
        TruckState stale = new TruckState("T-STALE", 11.0168, 76.9558, now.minusSeconds(40), null);
        assertFalse(stale.isAvailable(now, staleTimeoutSeconds));
    }

    @Test
    @DisplayName("Should find trucks in expanding concentric rings when center cell is empty")
    void shouldFindTruckInExpandingRings() {
        double warehouseLat = 11.0168;
        double warehouseLon = 76.9558;
        String centerCell = GridIndex.getCellId(warehouseLat, warehouseLon);

        // Put a truck 2 cells away
        int[] centerIdx = GridIndex.parseCellId(centerCell);
        String ring2Cell = GridIndex.formatCellId(centerIdx[0] + 2, centerIdx[1]);

        GridIndex.CellBounds bounds = GridIndex.getCellBounds(ring2Cell);
        TruckState truckInRing2 = new TruckState("T-RING2", bounds.centerLat(), bounds.centerLon(), Instant.now(), null, ring2Cell);

        List<String> ring0 = GridIndex.getRingCells(centerCell, 0);
        List<String> ring1 = GridIndex.getRingCells(centerCell, 1);
        List<String> ring2 = GridIndex.getRingCells(centerCell, 2);

        assertFalse(ring0.contains(ring2Cell));
        assertFalse(ring1.contains(ring2Cell));
        assertTrue(ring2.contains(ring2Cell));

        double dist = GridIndex.haversineDistance(warehouseLat, warehouseLon, truckInRing2.lat(), truckInRing2.lon());
        assertTrue(dist > 5.0 && dist < 30.0, "Distance should correspond to ~2 grid cells");
    }
}
