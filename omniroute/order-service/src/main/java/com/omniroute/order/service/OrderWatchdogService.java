package com.omniroute.order.service;

import com.omniroute.order.domain.Order;
import com.omniroute.order.domain.OrderStatus;
import com.omniroute.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class OrderWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(OrderWatchdogService.class);

    private final OrderRepository orderRepository;
    private final OrderSagaService sagaService;

    @Value("${saga.watchdog.stuck-timeout-seconds:30}")
    private long stuckTimeoutSeconds;

    public OrderWatchdogService(OrderRepository orderRepository, OrderSagaService sagaService) {
        this.orderRepository = orderRepository;
        this.sagaService = sagaService;
    }

    @Scheduled(fixedDelayString = "${saga.watchdog.interval-ms:10000}")
    public void scanStuckOrders() {
        Instant cutoff = Instant.now().minusSeconds(stuckTimeoutSeconds);
        Set<OrderStatus> stuckCandidates = Set.of(
                OrderStatus.INVENTORY_LOCKED,
                OrderStatus.PAYMENT_COMPLETED
        );

        List<Order> stuck = orderRepository.findStuckOrders(stuckCandidates, cutoff);
        if (!stuck.isEmpty()) {
            log.info("Watchdog detected {} stuck orders older than {}s", stuck.size(), stuckTimeoutSeconds);
            for (Order order : stuck) {
                try {
                    sagaService.compensateStuckOrder(order, "Saga timeout: stuck in " + order.getStatus() + " > " + stuckTimeoutSeconds + "s");
                } catch (Exception e) {
                    log.error("Failed to compensate stuck order {}: {}", order.getId(), e.getMessage(), e);
                }
            }
        }
    }
}
