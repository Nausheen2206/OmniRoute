package com.omniroute.payment.messaging;

import com.omniroute.common.events.OmniRouteEvent;
import com.omniroute.common.events.PaymentRequested;
import com.omniroute.common.events.RefundPayment;
import com.omniroute.common.events.Topics;
import com.omniroute.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;

    public PaymentEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service-cmd-group")
    public void handlePaymentCommands(OmniRouteEvent event) {
        if (event == null) return;
        MDC.put("orderId", event.orderId());
        try {
            log.info("Received event on payment-commands: {} for order {}", event.getClass().getSimpleName(), event.orderId());
            if (event instanceof PaymentRequested requested) {
                paymentService.processPaymentRequested(requested);
            } else if (event instanceof RefundPayment refund) {
                paymentService.processRefundPayment(refund);
            }
        } finally {
            MDC.remove("orderId");
        }
    }
}
