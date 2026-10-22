package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssignmentDto(
        String orderId,
        String truckId,
        double distanceKm,
        Instant assignedAt
) {
}
