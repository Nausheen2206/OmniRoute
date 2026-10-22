package com.omniroute.fleet.streams.spatial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure spatial indexing utility that partitions geospatial coordinates into discrete
 * rectangular grid cells and provides concentric ring expansions for spatial radius queries.
 */
public final class GridIndex {

    public static final double DEFAULT_CELL_SIZE = 0.05; // ~5.5 km at equator
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GridIndex() {}

    public static boolean isValidCoordinate(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon)
                && lat >= -90.0 && lat <= 90.0
                && lon >= -180.0 && lon <= 180.0;
    }

    public static String getCellId(double lat, double lon) {
        return getCellId(lat, lon, DEFAULT_CELL_SIZE);
    }

    public static String getCellId(double lat, double lon, double cellSize) {
        int latIdx = getLatIndex(lat, cellSize);
        int lonIdx = getLonIndex(lon, cellSize);
        return formatCellId(latIdx, lonIdx);
    }

    public static int getLatIndex(double lat, double cellSize) {
        return (int) Math.floor(lat / cellSize);
    }

    public static int getLonIndex(double lon, double cellSize) {
        return (int) Math.floor(lon / cellSize);
    }

    public static String formatCellId(int latIdx, int lonIdx) {
        return "c_" + latIdx + "_" + lonIdx;
    }

    public static int[] parseCellId(String cellId) {
        if (cellId == null || !cellId.startsWith("c_")) {
            throw new IllegalArgumentException("Invalid cellId format: " + cellId);
        }
        String[] parts = cellId.substring(2).split("_");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid cellId format: " + cellId);
        }
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    /**
     * Returns cells lying on the square perimeter at Chebyshev distance {@code ringRadius}
     * from the center cell.
     * Ring 0: 1 cell (center cell)
     * Ring k (k >= 1): 8*k cells
     */
    public static List<String> getRingCells(String centerCellId, int ringRadius) {
        if (ringRadius < 0) {
            throw new IllegalArgumentException("Ring radius must be non-negative: " + ringRadius);
        }
        if (ringRadius == 0) {
            return Collections.singletonList(centerCellId);
        }

        int[] coords = parseCellId(centerCellId);
        int centerLatIdx = coords[0];
        int centerLonIdx = coords[1];

        List<String> ring = new ArrayList<>(ringRadius * 8);

        // Top and bottom horizontal edges
        for (int dx = -ringRadius; dx <= ringRadius; dx++) {
            ring.add(formatCellId(centerLatIdx + ringRadius, centerLonIdx + dx));
            ring.add(formatCellId(centerLatIdx - ringRadius, centerLonIdx + dx));
        }

        // Left and right vertical edges (excluding corners already added)
        for (int dy = -ringRadius + 1; dy <= ringRadius - 1; dy++) {
            ring.add(formatCellId(centerLatIdx + dy, centerLonIdx + ringRadius));
            ring.add(formatCellId(centerLatIdx + dy, centerLonIdx - ringRadius));
        }

        return ring;
    }

    /**
     * Returns all cells from ring 0 up to {@code maxRings} in concentric order.
     */
    public static List<String> getCellsUpToRing(String centerCellId, int maxRings) {
        List<String> allCells = new ArrayList<>();
        for (int r = 0; r <= maxRings; r++) {
            allCells.addAll(getRingCells(centerCellId, r));
        }
        return allCells;
    }

    public static CellBounds getCellBounds(String cellId) {
        return getCellBounds(cellId, DEFAULT_CELL_SIZE);
    }

    public static CellBounds getCellBounds(String cellId, double cellSize) {
        int[] coords = parseCellId(cellId);
        double minLat = coords[0] * cellSize;
        double maxLat = minLat + cellSize;
        double minLon = coords[1] * cellSize;
        double maxLon = minLon + cellSize;
        double centerLat = minLat + cellSize / 2.0;
        double centerLon = minLon + cellSize / 2.0;

        return new CellBounds(cellId, minLat, maxLat, minLon, maxLon, centerLat, centerLon);
    }

    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(rLat1) * Math.cos(rLat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    public record CellBounds(
            String cellId,
            double minLat,
            double maxLat,
            double minLon,
            double maxLon,
            double centerLat,
            double centerLon
    ) {}
}
