package com.omniroute.order.service;

import com.omniroute.order.domain.DltMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DltMessageService {

    private static final Logger log = LoggerFactory.getLogger(DltMessageService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ConcurrentLinkedDeque<DltMessage> messages = new ConcurrentLinkedDeque<>();
    private final Map<String, DltMessage> messageMap = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public DltMessageService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topicPattern = ".*\\.DLT", containerFactory = "dltKafkaListenerContainerFactory", groupId = "order-service-dlt-inspector")
    public void onDltMessage(ConsumerRecord<String, String> record) {
        String originalTopic = extractHeader(record, "kafka_dlt-original-topic");
        if (originalTopic == null && record.topic().endsWith(".DLT")) {
            originalTopic = record.topic().substring(0, record.topic().length() - 4);
        }

        String error = extractHeader(record, "kafka_dlt-exception-message");
        if (error == null) {
            error = extractHeader(record, "kafka_dlt-exception-fqcn");
        }
        if (error == null) {
            error = "Processing/deserialization failure";
        }

        String id = String.valueOf(idGenerator.getAndIncrement());
        DltMessage msg = new DltMessage(
                id,
                record.topic(),
                originalTopic,
                record.key(),
                record.value(),
                error,
                Instant.ofEpochMilli(record.timestamp())
        );

        messages.addFirst(msg);
        messageMap.put(id, msg);
        log.info("Captured dead-lettered message [id={} topic={} originalTopic={} error={}]",
                id, record.topic(), originalTopic, error);

        // Retain only latest 500 messages
        while (messages.size() > 500) {
            DltMessage removed = messages.removeLast();
            if (removed != null) {
                messageMap.remove(removed.id());
            }
        }
    }

    public List<DltMessage> getRecentMessages() {
        return new ArrayList<>(messages);
    }

    public boolean replay(String id) {
        DltMessage msg = messageMap.get(id);
        if (msg == null || msg.originalTopic() == null) {
            log.warn("Cannot replay DLT message {}: not found or missing original topic", id);
            return false;
        }

        log.info("Replaying DLT message {} back to original topic {}", id, msg.originalTopic());
        kafkaTemplate.send(msg.originalTopic(), msg.key(), msg.payload());
        return true;
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
