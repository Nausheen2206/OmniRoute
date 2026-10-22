package com.omniroute.fleet.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.OrderAllocated;
import com.omniroute.common.events.Topics;
import com.omniroute.common.events.TruckAssigned;
import com.omniroute.common.events.TruckPositionUpdated;
import com.omniroute.fleet.streams.domain.TruckState;
import com.omniroute.fleet.streams.service.FleetStreamsQueryService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Disabled("Requires Docker TCP socket on Windows Docker Desktop 29; verified via TopologyTestDriver and EmbeddedKafka")
@Testcontainers
@SpringBootTest
@DirtiesContext
class FleetRoutingStreamsIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("fleet.state-dir", () -> System.getProperty("java.io.tmpdir") + "/tc-fleet-" + UUID.randomUUID());
        registry.add("fleet.processing-guarantee", () -> "at_least_once");
    }

    @Autowired
    private FleetStreamsQueryService queryService;

    @Autowired
    private ObjectMapper objectMapper;

    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> consumer;

    @BeforeAll
    static void initClients() {
        Properties prodProps = new Properties();
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        prodProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        prodProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(prodProps);

        Properties consProps = new Properties();
        consProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consProps.put(ConsumerConfig.GROUP_ID_CONFIG, "tc-routing-test-group-" + UUID.randomUUID());
        consProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(consProps);
        consumer.subscribe(Collections.singletonList(Topics.ROUTING_COMMANDS));
    }

    @AfterAll
    static void closeClients() {
        if (producer != null) producer.close();
        if (consumer != null) consumer.close();
    }

    @Test
    @DisplayName("End-to-end Kafka Streams integration with real Kafka Testcontainer")
    void testEndToEndStreamsRouting() throws Exception {
        String truckId = "TC-TRUCK-01";
        double lat = 11.0168;
        double lon = 76.9558;

        // 1. Publish GPS update to fleet-gps
        TruckPositionUpdated gps = new TruckPositionUpdated(truckId, lat, lon);
        producer.send(new ProducerRecord<>(Topics.FLEET_GPS, truckId, objectMapper.writeValueAsString(gps))).get();

        // 2. Wait until Streams interactive query sees the truck
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Collection<TruckState> trucks = queryService.getAllTrucks();
            assertThat(trucks).anyMatch(t -> t.truckId().equals(truckId));
        });

        // 3. Publish OrderAllocated event
        String orderId = "tc-order-1001";
        OrderAllocated allocated = new OrderAllocated(orderId, lat, lon);
        producer.send(new ProducerRecord<>(Topics.ORDER_EVENTS, orderId, objectMapper.writeValueAsString(allocated))).get();

        // 4. Poll routing-commands until TruckAssigned arrives
        AtomicReference<TruckAssigned> assignedRef = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            var records = consumer.poll(Duration.ofMillis(300));
            for (ConsumerRecord<String, String> rec : records) {
                OmniRouteEvent evt = objectMapper.readValue(rec.value(), OmniRouteEvent.class);
                if (evt instanceof TruckAssigned ta && ta.orderId().equals(orderId)) {
                    assignedRef.set(ta);
                    break;
                }
            }
            assertThat(assignedRef.get()).isNotNull();
        });

        TruckAssigned assigned = assignedRef.get();
        assertThat(assigned.orderId()).isEqualTo(orderId);
        assertThat(assigned.truckId()).isEqualTo(truckId);
        assertThat(assigned.distanceKm()).isLessThan(1.0);
    }
}
