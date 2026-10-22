package com.omniroute.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 100, nullable = false)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.createdAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
}
