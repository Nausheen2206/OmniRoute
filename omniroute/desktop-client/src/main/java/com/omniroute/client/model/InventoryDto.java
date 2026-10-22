package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryDto(
        String sku,
        int availableQty,
        int reservedQty,
        int totalQty
) {
    public double getAvailabilityRatio() {
        if (totalQty <= 0) return 0.0;
        return (double) availableQty / totalQty;
    }
}
