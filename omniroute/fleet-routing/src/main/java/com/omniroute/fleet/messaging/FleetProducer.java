package com.omniroute.fleet.messaging;

import com.omniroute.common.events.Topics;
import com.omniroute.common.events.TruckAssigned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FleetProducer {

    private static final Logger log = LoggerFactory.getLogger(FleetProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FleetProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTruckAssigned(TruckAssigned event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing TruckAssigned to {} for orderId {}, truckId {}, distance {} km",
                    Topics.ROUTING_COMMANDS, event.orderId(), event.truckId(), event.distanceKm());
            kafkaTemplate.send(Topics.ROUTING_COMMANDS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }
}
