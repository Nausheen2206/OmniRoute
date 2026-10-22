package com.omniroute.warehouse.messaging;

import com.omniroute.common.events.InventoryLockFailed;
import com.omniroute.common.events.InventoryLocked;
import com.omniroute.common.events.InventoryReleased;
import com.omniroute.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class WarehouseEventProducer {

    private static final Logger log = LoggerFactory.getLogger(WarehouseEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WarehouseEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishInventoryLocked(InventoryLocked event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing InventoryLocked to {} for orderId {}", Topics.WAREHOUSE_EVENTS, event.orderId());
            kafkaTemplate.send(Topics.WAREHOUSE_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishInventoryLockFailed(InventoryLockFailed event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing InventoryLockFailed to {} for orderId {}: {}",
                    Topics.WAREHOUSE_EVENTS, event.orderId(), event.reason());
            kafkaTemplate.send(Topics.WAREHOUSE_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishInventoryReleased(InventoryReleased event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing InventoryReleased to {} for orderId {}", Topics.WAREHOUSE_EVENTS, event.orderId());
            kafkaTemplate.send(Topics.WAREHOUSE_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }
}
