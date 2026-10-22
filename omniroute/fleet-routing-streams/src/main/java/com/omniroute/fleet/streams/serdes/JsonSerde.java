package com.omniroute.fleet.streams.serdes;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.TruckAssigned;
import com.omniroute.common.events.TruckPositionUpdated;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JsonSerde<T> implements Serde<T>, Serializer<T>, Deserializer<T> {

    private final ObjectMapper objectMapper;
    private final Class<T> targetClass;

    public JsonSerde(Class<T> targetClass) {
        this(targetClass, createDefaultObjectMapper());
    }

    public JsonSerde(Class<T> targetClass, ObjectMapper objectMapper) {
        this.targetClass = targetClass;
        this.objectMapper = objectMapper;
    }

    public static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing JSON for class: " + targetClass.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            // If the target is OmniRouteEvent, inspect node to gracefully handle events lacking type property
            if (OmniRouteEvent.class.isAssignableFrom(targetClass)) {
                JsonNode root = objectMapper.readTree(data);
                if (!root.has("type")) {
                    if (root.has("truckId") && root.has("lat")) {
                        return (T) objectMapper.treeToValue(root, TruckPositionUpdated.class);
                    } else if (root.has("warehouseLat")) {
                        return (T) objectMapper.treeToValue(root, OrderAllocated.class);
                    } else if (root.has("truckId") && root.has("distanceKm")) {
                        return (T) objectMapper.treeToValue(root, TruckAssigned.class);
                    }
                }
            }
            return objectMapper.readValue(data, targetClass);
        } catch (Exception e) {
            String str = new String(data, StandardCharsets.UTF_8);
            throw new RuntimeException("Error deserializing JSON for " + targetClass.getName() + " from: " + str, e);
        }
    }

    @Override
    public void close() {}

    @Override
    public Serializer<T> serializer() {
        return this;
    }

    @Override
    public Deserializer<T> deserializer() {
        return this;
    }
}
