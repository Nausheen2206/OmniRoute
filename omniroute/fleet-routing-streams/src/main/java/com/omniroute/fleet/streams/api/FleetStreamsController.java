package com.omniroute.fleet.streams.api;

import com.omniroute.fleet.streams.domain.Assignment;
import com.omniroute.fleet.streams.domain.CellSummary;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.service.FleetStreamsQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/fleet")
public class FleetStreamsController {

    private final FleetStreamsQueryService queryService;

    public FleetStreamsController(FleetStreamsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/trucks")
    public ResponseEntity<Collection<TruckState>> getTrucks() {
        return ResponseEntity.ok(queryService.getAllTrucks());
    }

    @GetMapping("/trucks/assignments")
    public ResponseEntity<List<Assignment>> getAssignments() {
        return ResponseEntity.ok(queryService.getRecentAssignments());
    }

    @GetMapping("/cells")
    public ResponseEntity<List<CellSummary>> getCells() {
        return ResponseEntity.ok(queryService.getActiveCells());
    }
}
