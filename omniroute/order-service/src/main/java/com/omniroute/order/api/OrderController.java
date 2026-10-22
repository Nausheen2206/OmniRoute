package com.omniroute.order.api;

import com.omniroute.order.api.dto.CreateOrderRequest;
import com.omniroute.order.api.dto.OrderResponse;
import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.SagaLog;
import com.omniroute.order.repository.OrderRepository;
import com.omniroute.order.repository.SagaLogRepository;
import com.omniroute.order.service.OrderSagaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order Placement and Saga Status API")
public class OrderController {

    private final OrderSagaService sagaService;
    private final OrderRepository orderRepository;
    private final SagaLogRepository sagaLogRepository;

    public OrderController(OrderSagaService sagaService,
                           OrderRepository orderRepository,
                           SagaLogRepository sagaLogRepository) {
        this.sagaService = sagaService;
        this.orderRepository = orderRepository;
        this.sagaLogRepository = sagaLogRepository;
    }

    @PostMapping
    @Operation(summary = "Place a new order (begins Saga)")
    public ResponseEntity<Map<String, Object>> placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = sagaService.createOrder(request.customerId(), request.items(), request.totalAmount());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "message", "Order accepted for processing"
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details and full Saga history")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(order -> {
                    List<SagaLog> history = sagaLogRepository.findByOrderIdOrderByCreatedAtAsc(id);
                    return ResponseEntity.ok(OrderResponse.from(order, history));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all recent orders")
    public ResponseEntity<List<OrderResponse>> getRecentOrders() {
        List<Order> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<OrderResponse> responses = orders.stream()
                .map(order -> OrderResponse.from(order, List.of()))
                .toList();
        return ResponseEntity.ok(responses);
    }
}
