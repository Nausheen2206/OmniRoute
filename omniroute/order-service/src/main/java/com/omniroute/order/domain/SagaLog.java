package com.omniroute.order.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "saga_log")
public class SagaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "step", length = 100, nullable = false)
    private String step;

    @Column(name = "event_id", length = 100, unique = true, nullable = false)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SagaLog() {}

    public SagaLog(String orderId, String step, String eventId) {
        this.orderId = orderId;
        this.step = step;
        this.eventId = eventId;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getStep() { return step; }
    public String getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
}
