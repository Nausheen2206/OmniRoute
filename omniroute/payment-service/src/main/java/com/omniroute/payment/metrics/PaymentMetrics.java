package com.omniroute.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter chargesCounter;
    private final Counter refundsCounter;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.chargesCounter = Counter.builder("omniroute.payment.charges")
                .description("Total number of successful payment charges")
                .register(meterRegistry);

        this.refundsCounter = Counter.builder("omniroute.payment.refunds")
                .description("Total number of payment refunds processed")
                .register(meterRegistry);
    }

    public void recordCharge() {
        chargesCounter.increment();
    }

    public void recordFailure(String reason) {
        String sanitizedReason = (reason != null && !reason.isBlank()) ? reason : "Unknown";
        if (sanitizedReason.contains("13.13")) {
            sanitizedReason = "SimulatedPaymentFailure";
        } else if (sanitizedReason.contains("declined")) {
            sanitizedReason = "CardDeclined";
        } else if (sanitizedReason.length() > 30) {
            sanitizedReason = sanitizedReason.substring(0, 30);
        }

        Counter.builder("omniroute.payment.failures")
                .description("Total number of failed payment authorizations")
                .tag("reason", sanitizedReason)
                .register(meterRegistry)
                .increment();
    }

    public void recordRefund() {
        refundsCounter.increment();
    }
}
