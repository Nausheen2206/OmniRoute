package com.omniroute.warehouse.service;

import com.omniroute.common.events.*;
import com.omniroute.warehouse.domain.Inventory;
import com.omniroute.warehouse.domain.ProcessedEvent;
import com.omniroute.warehouse.domain.Reservation;
import com.omniroute.warehouse.domain.ReservationStatus;
import com.omniroute.warehouse.messaging.WarehouseEventProducer;
import com.omniroute.warehouse.repository.InventoryRepository;
import com.omniroute.warehouse.repository.ProcessedEventRepository;
import com.omniroute.warehouse.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final WarehouseEventProducer producer;
    private final com.omniroute.warehouse.metrics.WarehouseMetrics warehouseMetrics;

    public WarehouseService(InventoryRepository inventoryRepository,
                            ReservationRepository reservationRepository,
                            ProcessedEventRepository processedEventRepository,
                            WarehouseEventProducer producer,
                            com.omniroute.warehouse.metrics.WarehouseMetrics warehouseMetrics) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.processedEventRepository = processedEventRepository;
        this.producer = producer;
        this.warehouseMetrics = warehouseMetrics;
    }

    @Transactional
    public void processOrderCreated(OrderCreated event) {
        MDC.put("orderId", event.orderId());
        try {
            if (processedEventRepository.existsById(event.eventId())) {
                log.warn("OrderCreated event {} already processed, skipping", event.eventId());
                return;
            }
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            List<OrderItem> items = event.items();
            if (items == null || items.isEmpty()) {
                producer.publishInventoryLockFailed(new InventoryLockFailed(event.orderId(), "Order contains no items"));
                return;
            }

            // Sort by SKU to avoid database deadlock during pessimistic locking
            List<OrderItem> sortedItems = items.stream()
                    .sorted(Comparator.comparing(OrderItem::sku))
                    .toList();

            Map<String, Inventory> lockedInventory = new HashMap<>();
            boolean allAvailable = true;
            String failureReason = null;

            for (OrderItem item : sortedItems) {
                Optional<Inventory> opt = inventoryRepository.findBySkuForUpdate(item.sku());
                if (opt.isEmpty()) {
                    allAvailable = false;
                    failureReason = "SKU not found: " + item.sku();
                    break;
                }
                Inventory inv = opt.get();
                if (inv.getAvailableQty() < item.quantity()) {
                    allAvailable = false;
                    failureReason = "Insufficient stock for SKU " + item.sku() +
                            ": requested " + item.quantity() + ", available " + inv.getAvailableQty();
                    break;
                }
                lockedInventory.put(item.sku(), inv);
            }

            if (!allAvailable) {
                log.info("Inventory check failed for order {}: {}", event.orderId(), failureReason);
                producer.publishInventoryLockFailed(new InventoryLockFailed(event.orderId(), failureReason));
                return;
            }

            // All items available: decrement stock and create ACTIVE reservations
            for (OrderItem item : sortedItems) {
                Inventory inv = lockedInventory.get(item.sku());
                inv.setAvailableQty(inv.getAvailableQty() - item.quantity());
                inventoryRepository.save(inv);

                Reservation res = new Reservation(event.orderId(), item.sku(), item.quantity(), ReservationStatus.ACTIVE);
                reservationRepository.save(res);
                warehouseMetrics.recordReservationCreated();
            }

            log.info("Successfully reserved inventory for order {}. Publishing InventoryLocked.", event.orderId());
            producer.publishInventoryLocked(new InventoryLocked(event.orderId()));
        } finally {
            MDC.remove("orderId");
        }
    }

    @Transactional
    public void processReleaseInventory(ReleaseInventory event) {
        MDC.put("orderId", event.orderId());
        try {
            if (processedEventRepository.existsById(event.eventId())) {
                log.warn("ReleaseInventory event {} already processed, skipping", event.eventId());
                return;
            }
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            List<Reservation> activeReservations = reservationRepository.findByOrderIdAndStatus(
                    event.orderId(), ReservationStatus.ACTIVE);

            if (activeReservations.isEmpty()) {
                log.info("No ACTIVE reservations for order {}. Idempotently publishing InventoryReleased.", event.orderId());
                producer.publishInventoryReleased(new InventoryReleased(event.orderId()));
                return;
            }

            for (Reservation res : activeReservations) {
                Optional<Inventory> opt = inventoryRepository.findBySkuForUpdate(res.getSku());
                if (opt.isPresent()) {
                    Inventory inv = opt.get();
                    inv.setAvailableQty(inv.getAvailableQty() + res.getQty());
                    inventoryRepository.save(inv);
                }
                res.setStatus(ReservationStatus.RELEASED);
                reservationRepository.save(res);
                warehouseMetrics.recordReservationReleased();
            }

            log.info("Released {} reservations for order {}. Restored stock. Publishing InventoryReleased.",
                    activeReservations.size(), event.orderId());
            producer.publishInventoryReleased(new InventoryReleased(event.orderId()));
        } finally {
            MDC.remove("orderId");
        }
    }

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public Optional<Inventory> getInventoryBySku(String sku) {
        return inventoryRepository.findById(sku);
    }
}
