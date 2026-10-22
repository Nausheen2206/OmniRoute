package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TruckDto(
        String truckId,
        double lat,
        double lon,
        String status,
        Instant lastPing
) {
}
