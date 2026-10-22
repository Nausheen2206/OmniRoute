package com.omniroute.order.api.dto;

import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import com.omniroute.order.domain.SagaLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String itemsJson,
        BigDecimal totalAmount,
        OrderStatus status,
        String failureReason,
        double warehouseLat,
        double warehouseLon,
        Instant createdAt,
        Instant updatedAt,
        List<SagaStepResponse> sagaHistory
) {
    public record SagaStepResponse(
            Long id,
            String step,
            String eventId,
            Instant createdAt
    ) {}

    public static OrderResponse from(Order order, List<SagaLog> sagaHistory) {
        List<SagaStepResponse> history = sagaHistory == null ? List.of() : sagaHistory.stream()
                .map(log -> new SagaStepResponse(log.getId(), log.getStep(), log.getEventId(), log.getCreatedAt()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getItemsJson(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getFailureReason(),
                order.getWarehouseLat(),
                order.getWarehouseLon(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                history
        );
    }
}
