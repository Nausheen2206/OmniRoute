package com.omniroute.warehouse.messaging;

import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderCreated;
import com.omniroute.common.events.ReleaseInventory;
import com.omniroute.common.events.Topics;
import com.omniroute.warehouse.service.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WarehouseEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WarehouseEventConsumer.class);

    private final WarehouseService warehouseService;

    public WarehouseEventConsumer(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "warehouse-service-order-group")
    public void handleOrderEvents(OmniRouteEvent event) {
        if (event == null) return;
        MDC.put("orderId", event.orderId());
        try {
            if (event instanceof OrderCreated orderCreated) {
                log.info("Received OrderCreated for order {}", orderCreated.orderId());
                warehouseService.processOrderCreated(orderCreated);
            }
        } finally {
            MDC.remove("orderId");
        }
    }

    @KafkaListener(topics = Topics.WAREHOUSE_COMMANDS, groupId = "warehouse-service-cmd-group")
    public void handleWarehouseCommands(OmniRouteEvent event) {
        if (event == null) return;
        MDC.put("orderId", event.orderId());
        try {
            if (event instanceof ReleaseInventory releaseInventory) {
                log.info("Received ReleaseInventory for order {}", releaseInventory.orderId());
                warehouseService.processReleaseInventory(releaseInventory);
            }
        } finally {
            MDC.remove("orderId");
        }
    }
}
