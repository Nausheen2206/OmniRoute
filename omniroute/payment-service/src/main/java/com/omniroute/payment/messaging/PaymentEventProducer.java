package com.omniroute.payment.messaging;

import com.omniroute.common.events.PaymentCompleted;
import com.omniroute.common.events.PaymentFailed;
import com.omniroute.common.events.PaymentRefunded;
import com.omniroute.common.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentCompleted(PaymentCompleted event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing PaymentCompleted to {} for orderId {}", Topics.PAYMENT_EVENTS, event.orderId());
            kafkaTemplate.send(Topics.PAYMENT_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishPaymentFailed(PaymentFailed event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing PaymentFailed to {} for orderId {}: {}",
                    Topics.PAYMENT_EVENTS, event.orderId(), event.reason());
            kafkaTemplate.send(Topics.PAYMENT_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }

    public void publishPaymentRefunded(PaymentRefunded event) {
        MDC.put("orderId", event.orderId());
        try {
            log.info("Publishing PaymentRefunded to {} for orderId {}", Topics.PAYMENT_EVENTS, event.orderId());
            kafkaTemplate.send(Topics.PAYMENT_EVENTS, event.orderId(), event);
        } finally {
            MDC.remove("orderId");
        }
    }
}
