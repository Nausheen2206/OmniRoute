package com.omniroute.client.model;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        String customerId,
        List<OrderItemDto> items,
        BigDecimal totalAmount
) {
}
