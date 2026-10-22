package com.omniroute.fleet.streams.domain;

import java.time.Instant;

public record Assignment(
        String orderId,
        String truckId,
        double distanceKm,
        Instant assignedAt
) {}
