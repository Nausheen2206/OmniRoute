package com.omniroute.payment.api;

import com.omniroute.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/demo")
public class DemoPaymentController {

    private static final Logger log = LoggerFactory.getLogger(DemoPaymentController.class);

    private final PaymentService paymentService;

    @Value("${demo.enabled:true}")
    private boolean demoEnabled;

    public DemoPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payment-config")
    public ResponseEntity<?> getPaymentConfig() {
        if (!demoEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Demo mode is disabled"));
        }
        return ResponseEntity.ok(Map.of(
                "failureRate", paymentService.getFailureRate(),
                "latencyMs", paymentService.getLatencyMs(),
                "demoEnabled", demoEnabled
        ));
    }

    @PostMapping("/payment-config")
    public ResponseEntity<?> updatePaymentConfig(@RequestBody PaymentConfigDto config) {
        if (!demoEnabled) {
            log.warn("Attempt to update payment config when demo.enabled=false");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Demo mode is disabled"));
        }

        if (config.failureRate() != null) {
            double rate = Math.max(0.0, Math.min(1.0, config.failureRate()));
            paymentService.setFailureRate(rate);
            log.info("Runtime update: payment.failure-rate set to {}", rate);
        }

        if (config.latencyMs() != null) {
            long latency = Math.max(0, config.latencyMs());
            paymentService.setLatencyMs(latency);
            log.info("Runtime update: payment.latency-ms set to {}", latency);
        }

        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "failureRate", paymentService.getFailureRate(),
                "latencyMs", paymentService.getLatencyMs()
        ));
    }
}
