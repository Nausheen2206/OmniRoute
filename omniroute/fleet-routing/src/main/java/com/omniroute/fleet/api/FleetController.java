package com.omniroute.fleet.api;

import com.omniroute.fleet.domain.Assignment;
import com.omniroute.fleet.domain.TruckState;
import com.omniroute.fleet.service.FleetRoutingEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/fleet")
public class FleetController {

    private final FleetRoutingEngine routingEngine;

    public FleetController(FleetRoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
    }

    @GetMapping("/trucks")
    public ResponseEntity<Collection<TruckState>> getTrucks() {
        return ResponseEntity.ok(routingEngine.getAllTrucks());
    }

    @GetMapping("/trucks/assignments")
    public ResponseEntity<List<Assignment>> getAssignments() {
        return ResponseEntity.ok(routingEngine.getRecentAssignments());
    }
}
