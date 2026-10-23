package com.omniroute.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private final HttpClient httpClient;

    @Value("${payment.service.url:http://localhost:8082}")
    private String paymentServiceUrl;

    public PaymentServiceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();
    }

    public PaymentServiceClient(String paymentServiceUrl) {
        this();
        this.paymentServiceUrl = paymentServiceUrl;
    }

    public void checkAvailability() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(paymentServiceUrl + "/actuator/health"))
                    .timeout(Duration.ofMillis(1500))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Payment service returned server error status: " + response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Payment service health probe failed against {}: {}", paymentServiceUrl, e.getMessage());
            throw new PaymentServiceUnavailableException("Payment service unavailable: " + e.getMessage(), e);
        }
    }

    public static class PaymentServiceUnavailableException extends RuntimeException {
        public PaymentServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
