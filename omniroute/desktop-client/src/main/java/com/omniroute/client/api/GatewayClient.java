package com.omniroute.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omniroute.client.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GatewayClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String baseUrl;

    public GatewayClient(String baseUrl) {
        this.baseUrl = normalizeUrl(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = normalizeUrl(baseUrl);
    }

    private String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "http://localhost:8080";
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public CompletableFuture<List<OrderDto>> fetchOrders() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<OrderDto>>() {});
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse orders response", e);
                        }
                    } else {
                        throw new RuntimeException("Server error (" + response.statusCode() + "): " + response.body());
                    }
                });
    }

    public CompletableFuture<OrderDto> fetchOrderDetails(String orderId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/" + orderId))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), OrderDto.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse order details", e);
                        }
                    } else {
                        throw new RuntimeException("Order not found or error (" + response.statusCode() + ")");
                    }
                });
    }

    public CompletableFuture<String> placeOrder(CreateOrderRequest orderRequest) {
        try {
            String jsonBody = objectMapper.writeValueAsString(orderRequest);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/orders"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return response.body();
                        } else {
                            throw new RuntimeException("Order failed (" + response.statusCode() + "): " + response.body());
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<List<InventoryDto>> fetchInventory() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/inventory"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<InventoryDto>>() {});
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse inventory", e);
                        }
                    } else {
                        throw new RuntimeException("Inventory error (" + response.statusCode() + ")");
                    }
                });
    }

    public CompletableFuture<List<TruckDto>> fetchTrucks() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/fleet/trucks"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<TruckDto>>() {});
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse trucks", e);
                        }
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    public CompletableFuture<List<AssignmentDto>> fetchAssignments() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/fleet/trucks/assignments"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), new TypeReference<List<AssignmentDto>>() {});
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse assignments", e);
                        }
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    public CompletableFuture<Boolean> checkHealth() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/actuator/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> res.statusCode() == 200)
                .exceptionally(ex -> false);
    }

    public CompletableFuture<PaymentConfigDto> fetchPaymentConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/demo/payment-config"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), PaymentConfigDto.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse payment config", e);
                        }
                    } else {
                        throw new RuntimeException("Payment config error (" + response.statusCode() + ")");
                    }
                });
    }

    public CompletableFuture<PaymentConfigDto> updatePaymentConfig(long latencyMs, double failureRate) {
        try {
            String json = objectMapper.writeValueAsString(new PaymentConfigDto(latencyMs, failureRate));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/demo/payment-config"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            try {
                                return objectMapper.readValue(response.body(), PaymentConfigDto.class);
                            } catch (Exception e) {
                                return new PaymentConfigDto(latencyMs, failureRate);
                            }
                        } else {
                            throw new RuntimeException("Failed updating payment config (" + response.statusCode() + "): " + response.body());
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<CircuitBreakerDto> fetchCircuitBreakerStatus() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/circuit-breaker"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            return objectMapper.readValue(response.body(), CircuitBreakerDto.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse circuit breaker response", e);
                        }
                    } else {
                        throw new RuntimeException("Circuit breaker endpoint error (" + response.statusCode() + ")");
                    }
                });
    }
}
