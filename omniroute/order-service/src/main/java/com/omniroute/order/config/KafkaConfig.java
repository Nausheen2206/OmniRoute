package com.omniroute.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omniroute.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

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
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(2000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.messaging.converter.MessageConversionException.class,
                org.apache.kafka.common.errors.SerializationException.class,
                IllegalArgumentException.class,
                ClassCastException.class
        );
        return errorHandler;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties(null));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> dltConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-dlt-inspector");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltKafkaListenerContainerFactory(
            ConsumerFactory<String, String> dltConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(dltConsumerFactory);
        return factory;
    }

    @Bean public NewTopic orderEventsTopic() { return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic warehouseEventsTopic() { return TopicBuilder.name(Topics.WAREHOUSE_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentEventsTopic() { return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic warehouseCommandsTopic() { return TopicBuilder.name(Topics.WAREHOUSE_COMMANDS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentCommandsTopic() { return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(3).replicas(1).build(); }
    @Bean public NewTopic fleetGpsTopic() { return TopicBuilder.name(Topics.FLEET_GPS).partitions(12).replicas(1).build(); }
    @Bean public NewTopic routingCommandsTopic() { return TopicBuilder.name(Topics.ROUTING_COMMANDS).partitions(3).replicas(1).build(); }

    // DLT Topics
    @Bean public NewTopic orderEventsDlt() { return TopicBuilder.name(Topics.ORDER_EVENTS + ".DLT").partitions(3).replicas(1).build(); }
    @Bean public NewTopic warehouseEventsDlt() { return TopicBuilder.name(Topics.WAREHOUSE_EVENTS + ".DLT").partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentEventsDlt() { return TopicBuilder.name(Topics.PAYMENT_EVENTS + ".DLT").partitions(3).replicas(1).build(); }
    @Bean public NewTopic warehouseCommandsDlt() { return TopicBuilder.name(Topics.WAREHOUSE_COMMANDS + ".DLT").partitions(3).replicas(1).build(); }
    @Bean public NewTopic paymentCommandsDlt() { return TopicBuilder.name(Topics.PAYMENT_COMMANDS + ".DLT").partitions(3).replicas(1).build(); }
    @Bean public NewTopic routingCommandsDlt() { return TopicBuilder.name(Topics.ROUTING_COMMANDS + ".DLT").partitions(3).replicas(1).build(); }
}
