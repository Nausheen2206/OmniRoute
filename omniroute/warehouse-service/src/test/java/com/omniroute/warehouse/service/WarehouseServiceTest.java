package com.omniroute.warehouse.service;

import com.omniroute.common.events.*;
import com.omniroute.warehouse.domain.Inventory;
import com.omniroute.warehouse.domain.Reservation;
import com.omniroute.warehouse.domain.ReservationStatus;
import com.omniroute.warehouse.messaging.WarehouseEventProducer;
import com.omniroute.warehouse.repository.InventoryRepository;
import com.omniroute.warehouse.repository.ProcessedEventRepository;
import com.omniroute.warehouse.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WarehouseServiceTest {

    private InventoryRepository inventoryRepository;
    private ReservationRepository reservationRepository;
    private ProcessedEventRepository processedEventRepository;
    private WarehouseEventProducer producer;
    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        producer = mock(WarehouseEventProducer.class);

        warehouseService = new WarehouseService(
                inventoryRepository,
                reservationRepository,
                processedEventRepository,
                producer
        );
    }

    @Test
    @DisplayName("Should successfully lock inventory when stock is sufficient")
    void shouldSuccessfullyLockInventory() {
        String orderId = "order-123";
        Inventory inv = new Inventory("SKU-001", 50);
        when(inventoryRepository.findBySkuForUpdate("SKU-001")).thenReturn(Optional.of(inv));
        when(processedEventRepository.existsById(any())).thenReturn(false);

        OrderCreated event = new OrderCreated(
                orderId, "cust-1", List.of(new OrderItem("SKU-001", 10)), BigDecimal.valueOf(100), 11.0, 76.0);

        warehouseService.processOrderCreated(event);

        assertThat(inv.getAvailableQty()).isEqualTo(40);
        verify(inventoryRepository).save(inv);
        verify(reservationRepository).save(any(Reservation.class));

        ArgumentCaptor<InventoryLocked> captor = ArgumentCaptor.forClass(InventoryLocked.class);
        verify(producer).publishInventoryLocked(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Should fail inventory lock when available stock is insufficient")
    void shouldFailWhenInsufficientStock() {
        String orderId = "order-456";
        Inventory inv = new Inventory("SKU-001", 5);
        when(inventoryRepository.findBySkuForUpdate("SKU-001")).thenReturn(Optional.of(inv));
        when(processedEventRepository.existsById(any())).thenReturn(false);

        OrderCreated event = new OrderCreated(
                orderId, "cust-1", List.of(new OrderItem("SKU-001", 10)), BigDecimal.valueOf(100), 11.0, 76.0);

        warehouseService.processOrderCreated(event);

        assertThat(inv.getAvailableQty()).isEqualTo(5); // unchanged
        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());

        ArgumentCaptor<InventoryLockFailed> captor = ArgumentCaptor.forClass(InventoryLockFailed.class);
        verify(producer).publishInventoryLockFailed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().reason()).contains("Insufficient stock");
    }

    @Test
    @DisplayName("Should fail inventory lock when SKU does not exist")
    void shouldFailWhenSkuNotFound() {
        String orderId = "order-789";
        when(inventoryRepository.findBySkuForUpdate("SKU-UNKNOWN")).thenReturn(Optional.empty());
        when(processedEventRepository.existsById(any())).thenReturn(false);

        OrderCreated event = new OrderCreated(
                orderId, "cust-1", List.of(new OrderItem("SKU-UNKNOWN", 2)), BigDecimal.valueOf(50), 11.0, 76.0);

        warehouseService.processOrderCreated(event);

        ArgumentCaptor<InventoryLockFailed> captor = ArgumentCaptor.forClass(InventoryLockFailed.class);
        verify(producer).publishInventoryLockFailed(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
        assertThat(captor.getValue().reason()).contains("SKU not found");
    }

    @Test
    @DisplayName("Should release inventory and restore stock on ReleaseInventory event")
    void shouldReleaseInventoryAndRestoreStock() {
        String orderId = "order-rel-1";
        Inventory inv = new Inventory("SKU-001", 40);
        Reservation res = new Reservation(orderId, "SKU-001", 10, ReservationStatus.ACTIVE);

        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE))
                .thenReturn(List.of(res));
        when(inventoryRepository.findBySkuForUpdate("SKU-001")).thenReturn(Optional.of(inv));

        ReleaseInventory event = new ReleaseInventory(orderId);
        warehouseService.processReleaseInventory(event);

        assertThat(inv.getAvailableQty()).isEqualTo(50); // Restored from 40 to 50
        assertThat(res.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(inventoryRepository).save(inv);
        verify(reservationRepository).save(res);

        ArgumentCaptor<InventoryReleased> captor = ArgumentCaptor.forClass(InventoryReleased.class);
        verify(producer).publishInventoryReleased(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Should handle duplicate ReleaseInventory idempotently without double-restoring stock")
    void shouldHandleDuplicateReleaseInventoryIdempotently() {
        String orderId = "order-rel-dup";
        when(processedEventRepository.existsById("dup-event-id")).thenReturn(true);

        ReleaseInventory dupEvent = new ReleaseInventory("dup-event-id", orderId, java.time.Instant.now());
        warehouseService.processReleaseInventory(dupEvent);

        verify(inventoryRepository, never()).findBySkuForUpdate(any());
        verify(inventoryRepository, never()).save(any());
        verify(producer, never()).publishInventoryReleased(any());
    }

    @Test
    @DisplayName("Should handle release when no active reservations exist idempotently")
    void shouldHandleReleaseWhenNoActiveReservationsExist() {
        String orderId = "order-no-res";
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        ReleaseInventory event = new ReleaseInventory(orderId);
        warehouseService.processReleaseInventory(event);

        verify(inventoryRepository, never()).findBySkuForUpdate(any());
        verify(inventoryRepository, never()).save(any());

        // Still publishes InventoryReleased to unblock Saga
        verify(producer).publishInventoryReleased(any());
    }
}
