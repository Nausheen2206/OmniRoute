package com.omniroute.warehouse.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_reservations_order_sku", columnNames = {"order_id", "sku"})
})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "sku", length = 100, nullable = false)
    private String sku;

    @Column(name = "qty", nullable = false)
    private int qty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Reservation() {}

    public Reservation(String orderId, String sku, int qty, ReservationStatus status) {
        this.orderId = orderId;
        this.sku = sku;
        this.qty = qty;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQty() { return qty; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
