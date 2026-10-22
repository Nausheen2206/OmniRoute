package com.omniroute.fleet.streams.serdes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.TruckAssigned;
import com.omniroute.common.events.TruckPositionUpdated;
import com.omniroute.fleet.streams.domain.TruckState;
import org.apache.kafka.common.serialization.Serde;

public final class CustomSerdes {

    private CustomSerdes() {}

    public static Serde<OmniRouteEvent> omniRouteEventSerde(ObjectMapper mapper) {
        return new JsonSerde<>(OmniRouteEvent.class, mapper);
    }

    public static Serde<TruckPositionUpdated> truckPositionSerde(ObjectMapper mapper) {
        return new JsonSerde<>(TruckPositionUpdated.class, mapper);
    }

    public static Serde<OrderAllocated> orderAllocatedSerde(ObjectMapper mapper) {
        return new JsonSerde<>(OrderAllocated.class, mapper);
    }

    public static Serde<TruckAssigned> truckAssignedSerde(ObjectMapper mapper) {
        return new JsonSerde<>(TruckAssigned.class, mapper);
    }

    public static Serde<TruckState> truckStateSerde(ObjectMapper mapper) {
        return new JsonSerde<>(TruckState.class, mapper);
    }
}
