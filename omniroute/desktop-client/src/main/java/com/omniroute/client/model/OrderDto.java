package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        @JsonAlias({"id", "orderId"}) String orderId,
        String customerId,
        List<OrderItemDto> items,
        String itemsJson,
        BigDecimal totalAmount,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<SagaStepDto> sagaHistory
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public OrderDto {
        if (items == null) items = Collections.emptyList();
        if (sagaHistory == null) sagaHistory = Collections.emptyList();
    }

    public String getItemsSummary() {
        if (items != null && !items.isEmpty()) {
            return items.stream()
                    .map(i -> i.sku() + " (x" + i.quantity() + ")")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        }
        if (itemsJson != null && !itemsJson.isBlank()) {
            try {
                List<OrderItemDto> parsed = MAPPER.readValue(itemsJson, new TypeReference<List<OrderItemDto>>() {});
                return parsed.stream()
                        .map(i -> i.sku() + " (x" + i.quantity() + ")")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(itemsJson);
            } catch (Exception e) {
                return itemsJson;
            }
        }
        return "None";
    }
}
