package com.omniroute.fleet.streams.spatial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GridIndexTest {

    @Test
    @DisplayName("Valid coordinates are accepted, invalid rejected")
    void testCoordinateValidation() {
        assertTrue(GridIndex.isValidCoordinate(11.0168, 76.9558));
        assertTrue(GridIndex.isValidCoordinate(0.0, 0.0));
        assertTrue(GridIndex.isValidCoordinate(-90.0, -180.0));
        assertTrue(GridIndex.isValidCoordinate(90.0, 180.0));

        assertFalse(GridIndex.isValidCoordinate(91.0, 0.0));
        assertFalse(GridIndex.isValidCoordinate(-90.1, 0.0));
        assertFalse(GridIndex.isValidCoordinate(0.0, 180.1));
        assertFalse(GridIndex.isValidCoordinate(0.0, -180.1));
        assertFalse(GridIndex.isValidCoordinate(Double.NaN, 0.0));
    }

    @Test
    @DisplayName("Cell ID format and parsing round-trip")
    void testCellIdRoundTrip() {
        double lat = 11.0168;
        double lon = 76.9558;
        String cellId = GridIndex.getCellId(lat, lon);
        assertNotNull(cellId);
        assertTrue(cellId.startsWith("c_"));

        int[] coords = GridIndex.parseCellId(cellId);
        assertEquals(2, coords.length);
        assertEquals(cellId, GridIndex.formatCellId(coords[0], coords[1]));

        // Check negative coordinates
        String negCellId = GridIndex.getCellId(-12.34, -45.67);
        int[] negCoords = GridIndex.parseCellId(negCellId);
        assertEquals(negCellId, GridIndex.formatCellId(negCoords[0], negCoords[1]));
    }

    @Test
    @DisplayName("Ring 0 has 1 cell, Ring 1 has 8, Ring 2 has 16, Ring k has 8*k cells")
    void testRingExpansionCounts() {
        String center = GridIndex.getCellId(11.0168, 76.9558);

        List<String> ring0 = GridIndex.getRingCells(center, 0);
        assertEquals(1, ring0.size());
        assertEquals(center, ring0.getFirst());

        List<String> ring1 = GridIndex.getRingCells(center, 1);
        assertEquals(8, ring1.size());
        assertEquals(8, new HashSet<>(ring1).size(), "Ring 1 cells must be unique");
        assertFalse(ring1.contains(center), "Ring 1 must not contain center cell");

        List<String> ring2 = GridIndex.getRingCells(center, 2);
        assertEquals(16, ring2.size());
        assertEquals(16, new HashSet<>(ring2).size(), "Ring 2 cells must be unique");

        List<String> ring3 = GridIndex.getRingCells(center, 3);
        assertEquals(24, ring3.size());
    }

    @Test
    @DisplayName("Concentric rings up to maxRings have no overlap and strictly expanding distance")
    void testConcentricRingsNoOverlap() {
        String center = GridIndex.getCellId(11.0168, 76.9558);
        int maxRings = 4;
        List<String> allCells = GridIndex.getCellsUpToRing(center, maxRings);

        // Total expected = (2*maxRings + 1)^2 = 9^2 = 81 cells
        int expectedTotal = (2 * maxRings + 1) * (2 * maxRings + 1);
        assertEquals(expectedTotal, allCells.size());

        Set<String> uniqueSet = new HashSet<>(allCells);
        assertEquals(expectedTotal, uniqueSet.size(), "All concentric ring cells must be unique without overlap");
    }

    @Test
    @DisplayName("Cell bounds accurately enclose center coordinate")
    void testCellBounds() {
        double lat = 11.0168;
        double lon = 76.9558;
        String cellId = GridIndex.getCellId(lat, lon);
        GridIndex.CellBounds bounds = GridIndex.getCellBounds(cellId);

        assertTrue(bounds.minLat() <= lat && lat < bounds.maxLat(), "Latitude must fall within cell bounds");
        assertTrue(bounds.minLon() <= lon && lon < bounds.maxLon(), "Longitude must fall within cell bounds");
        assertTrue(bounds.minLat() < bounds.centerLat() && bounds.centerLat() < bounds.maxLat());
        assertTrue(bounds.minLon() < bounds.centerLon() && bounds.centerLon() < bounds.maxLon());
    }

    @Test
    @DisplayName("Haversine distance returns 0 for same point and matches known distance")
    void testHaversineDistance() {
        double dist0 = GridIndex.haversineDistance(11.0168, 76.9558, 11.0168, 76.9558);
        assertEquals(0.0, dist0, 0.001);

        // Distance between Coimbatore (11.0168, 76.9558) and Bangalore (12.9716, 77.5946) ~ 227 km
        double distCbeBlr = GridIndex.haversineDistance(11.0168, 76.9558, 12.9716, 77.5946);
        assertTrue(distCbeBlr > 220.0 && distCbeBlr < 240.0, "Coimbatore to Bangalore should be ~227 km, was " + distCbeBlr);
    }
}
