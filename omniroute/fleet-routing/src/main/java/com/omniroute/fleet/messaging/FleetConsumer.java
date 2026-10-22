package com.omniroute.fleet.messaging;

import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.Topics;
import com.omniroute.common.events.TruckAssigned;
import com.omniroute.common.events.TruckPositionUpdated;
import com.omniroute.fleet.domain.Assignment;
import com.omniroute.fleet.service.FleetRoutingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FleetConsumer {

    private static final Logger log = LoggerFactory.getLogger(FleetConsumer.class);

    private final FleetRoutingEngine routingEngine;
    private final FleetProducer producer;

    public FleetConsumer(FleetRoutingEngine routingEngine, FleetProducer producer) {
        this.routingEngine = routingEngine;
        this.producer = producer;
    }

    @KafkaListener(topics = Topics.FLEET_GPS, groupId = "fleet-routing-gps-group")
    public void handleGpsUpdate(OmniRouteEvent event) {
        if (event instanceof TruckPositionUpdated gps) {
            routingEngine.updatePosition(gps.truckId(), gps.lat(), gps.lon(), gps.timestamp());
        }
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "fleet-routing-order-group")
    public void handleOrderEvents(OmniRouteEvent event) {
        if (event instanceof OrderAllocated allocated) {
            MDC.put("orderId", allocated.orderId());
            try {
                log.info("Received OrderAllocated for order {}. Searching nearest truck...", allocated.orderId());
                Optional<Assignment> assignmentOpt = routingEngine.assignNearestTruck(
                        allocated.orderId(), allocated.warehouseLat(), allocated.warehouseLon());

                if (assignmentOpt.isPresent()) {
                    Assignment assignment = assignmentOpt.get();
                    log.info("Assigned truck {} to order {} (dist: {} km)",
                            assignment.truckId(), allocated.orderId(), assignment.distanceKm());
                    producer.publishTruckAssigned(new TruckAssigned(
                            allocated.orderId(), assignment.truckId(), assignment.distanceKm()));
                } else {
                    log.warn("No available truck for order {}. Watchdog will compensate.", allocated.orderId());
                }
            } finally {
                MDC.remove("orderId");
            }
        }
    }
}
