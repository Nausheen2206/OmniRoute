package com.omniroute.warehouse.api;

import com.omniroute.warehouse.domain.Inventory;
import com.omniroute.warehouse.service.WarehouseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public ResponseEntity<List<Inventory>> getAllInventory() {
        return ResponseEntity.ok(warehouseService.getAllInventory());
    }

    @GetMapping("/{sku}")
    public ResponseEntity<Inventory> getInventoryBySku(@PathVariable String sku) {
        return warehouseService.getInventoryBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
