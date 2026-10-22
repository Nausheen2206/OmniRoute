package com.omniroute.payment.service;

import com.omniroute.common.events.*;
import com.omniroute.payment.domain.Payment;
import com.omniroute.payment.domain.PaymentStatus;
import com.omniroute.payment.domain.ProcessedEvent;
import com.omniroute.payment.messaging.PaymentEventProducer;
import com.omniroute.payment.repository.PaymentRepository;
import com.omniroute.payment.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Random;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final BigDecimal FORCED_FAILURE_AMOUNT = new BigDecimal("13.13");
    private static final BigDecimal FORCED_SUCCESS_AMOUNT = new BigDecimal("1.00");

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentEventProducer producer;
    private final com.omniroute.payment.metrics.PaymentMetrics paymentMetrics;
    private final Random random = new Random();

    @Value("${payment.failure-rate:0.0}")
    private double failureRate;

    @Value("${payment.latency-ms:0}")
    private long latencyMs;

    public PaymentService(PaymentRepository paymentRepository,
                          ProcessedEventRepository processedEventRepository,
                          PaymentEventProducer producer,
                          com.omniroute.payment.metrics.PaymentMetrics paymentMetrics) {
        this.paymentRepository = paymentRepository;
        this.processedEventRepository = processedEventRepository;
        this.producer = producer;
        this.paymentMetrics = paymentMetrics;
    }

    @Transactional
    public void processPaymentRequested(PaymentRequested event) {
        MDC.put("orderId", event.orderId());
        try {
            if (processedEventRepository.existsById(event.eventId())) {
                log.warn("PaymentRequested event {} already processed, skipping", event.eventId());
                return;
            }
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            Optional<Payment> existingOpt = paymentRepository.findByOrderId(event.orderId());
            if (existingOpt.isPresent()) {
                Payment existing = existingOpt.get();
                log.warn("Payment already recorded for order {} with status {}", event.orderId(), existing.getStatus());
                if (existing.getStatus() == PaymentStatus.CHARGED) {
                    producer.publishPaymentCompleted(new PaymentCompleted(event.orderId()));
                } else if (existing.getStatus() == PaymentStatus.FAILED) {
                    producer.publishPaymentFailed(new PaymentFailed(event.orderId(), existing.getReason()));
                }
                return;
            }

            // Simulate artificial latency if configured
            if (latencyMs > 0) {
                try {
                    log.info("Simulating payment latency of {} ms", latencyMs);
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            boolean shouldFail = evaluateFailure(event.amount());

            if (shouldFail) {
                String reason = "Card declined: Payment failed";
                if (event.amount() != null && event.amount().compareTo(FORCED_FAILURE_AMOUNT) == 0) {
                    reason = "Card declined: Deterministic test failure (total 13.13)";
                }
                log.info("Payment FAILED for order {}: {}", event.orderId(), reason);
                Payment payment = new Payment(event.orderId(), PaymentStatus.FAILED, event.amount(), reason);
                paymentRepository.save(payment);
                paymentMetrics.recordFailure(reason);

                producer.publishPaymentFailed(new PaymentFailed(event.orderId(), reason));
            } else {
                log.info("Payment SUCCESS for order {}, amount {}", event.orderId(), event.amount());
                Payment payment = new Payment(event.orderId(), PaymentStatus.CHARGED, event.amount(), "Payment successful");
                paymentRepository.save(payment);
                paymentMetrics.recordCharge();

                producer.publishPaymentCompleted(new PaymentCompleted(event.orderId()));
            }
        } finally {
            MDC.remove("orderId");
        }
    }

    private boolean evaluateFailure(BigDecimal amount) {
        if (amount == null) return false;
        if (amount.compareTo(FORCED_FAILURE_AMOUNT) == 0) {
            return true;
        }
        if (amount.compareTo(FORCED_SUCCESS_AMOUNT) == 0) {
            return false;
        }
        if (failureRate <= 0.0) {
            return false;
        }
        return random.nextDouble() < failureRate;
    }

    @Transactional
    public void processRefundPayment(RefundPayment event) {
        MDC.put("orderId", event.orderId());
        try {
            if (processedEventRepository.existsById(event.eventId())) {
                log.warn("RefundPayment event {} already processed, skipping", event.eventId());
                return;
            }
            processedEventRepository.save(new ProcessedEvent(event.eventId()));

            Optional<Payment> opt = paymentRepository.findByOrderId(event.orderId());
            if (opt.isPresent()) {
                Payment payment = opt.get();
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setReason("Refunded via Saga compensation");
                paymentRepository.save(payment);
                log.info("Payment refunded for order {}", event.orderId());
            } else {
                log.warn("No prior payment record for order {} during refund. Recording REFUNDED.", event.orderId());
                Payment payment = new Payment(event.orderId(), PaymentStatus.REFUNDED, BigDecimal.ZERO, "Refund recorded without prior charge");
                paymentRepository.save(payment);
            }
            paymentMetrics.recordRefund();

            producer.publishPaymentRefunded(new PaymentRefunded(event.orderId()));
        } finally {
            MDC.remove("orderId");
        }
    }
}
