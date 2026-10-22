package com.omniroute.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omniroute.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean public NewTopic orderEventsTopic() { return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic warehouseEventsTopic() { return TopicBuilder.name(Topics.WAREHOUSE_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentEventsTopic() { return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic warehouseCommandsTopic() { return TopicBuilder.name(Topics.WAREHOUSE_COMMANDS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentCommandsTopic() { return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic fleetGpsTopic() { return TopicBuilder.name(Topics.FLEET_GPS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic routingCommandsTopic() { return TopicBuilder.name(Topics.ROUTING_COMMANDS).partitions(3).replicas(1).build(); }
}
