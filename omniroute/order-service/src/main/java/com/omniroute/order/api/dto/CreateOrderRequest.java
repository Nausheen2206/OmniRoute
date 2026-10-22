package com.omniroute.order.api.dto;

import com.omniroute.common.events.OrderItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "customerId is required")
        String customerId,

        @NotEmpty(message = "items list cannot be empty")
        List<OrderItem> items,

        @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than zero")
        BigDecimal totalAmount
) {}
