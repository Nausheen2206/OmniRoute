package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        String orderId,
        String customerId,
        List<OrderItemDto> items,
        BigDecimal totalAmount,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<SagaStepDto> sagaHistory
) {
    public OrderDto {
        if (items == null) items = Collections.emptyList();
        if (sagaHistory == null) sagaHistory = Collections.emptyList();
    }

    public String getItemsSummary() {
        if (items == null || items.isEmpty()) return "None";
        return items.stream()
                .map(i -> i.sku() + " (x" + i.quantity() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
