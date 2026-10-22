package com.omniroute.fleet.streams;

import com.omniroute.common.events.Topics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {Topics.FLEET_GPS, Topics.ORDER_EVENTS, Topics.ROUTING_COMMANDS}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "fleet.state-dir=${java.io.tmpdir}/kafka-streams-test-${random.uuid}",
        "fleet.processing-guarantee=at_least_once"
})
class FleetRoutingStreamsApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should successfully expose /fleet/trucks endpoint")
    void testGetTrucksEndpoint() throws Exception {
        mockMvc.perform(get("/fleet/trucks"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should successfully expose /fleet/trucks/assignments endpoint")
    void testGetAssignmentsEndpoint() throws Exception {
        mockMvc.perform(get("/fleet/trucks/assignments"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should successfully expose /fleet/cells endpoint")
    void testGetCellsEndpoint() throws Exception {
        mockMvc.perform(get("/fleet/cells"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should expose /actuator/prometheus endpoint with custom fleet metrics")
    void testPrometheusEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
