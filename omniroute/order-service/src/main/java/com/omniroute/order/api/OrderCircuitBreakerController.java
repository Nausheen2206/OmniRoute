package com.omniroute.order.api;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "Circuit Breaker", description = "Payment Circuit Breaker Live State API")
public class OrderCircuitBreakerController {

    private final CircuitBreaker circuitBreaker;

    public OrderCircuitBreakerController(CircuitBreakerRegistry registry) {
        this.circuitBreaker = registry.circuitBreaker("paymentService");
    }

    @GetMapping("/circuit-breaker")
    @Operation(summary = "Get live payment circuit breaker status")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStatus() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return ResponseEntity.ok(Map.of(
                "name", circuitBreaker.getName(),
                "state", circuitBreaker.getState().name(),
                "failureRate", metrics.getFailureRate(),
                "slowCallRate", metrics.getSlowCallRate(),
                "bufferedCalls", metrics.getNumberOfBufferedCalls(),
                "failedCalls", metrics.getNumberOfFailedCalls(),
                "successfulCalls", metrics.getNumberOfSuccessfulCalls(),
                "notPermittedCalls", metrics.getNumberOfNotPermittedCalls()
        ));
    }
}
