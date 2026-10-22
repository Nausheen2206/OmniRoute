package com.omniroute.fleet.streams.domain;

import com.omniroute.fleet.streams.spatial.GridIndex;
import java.util.List;

public record CellSummary(
        String cellId,
        GridIndex.CellBounds bounds,
        int truckCount,
        int activeTrucks,
        List<String> truckIds,
        long windowedTelemetryCount
) {}
