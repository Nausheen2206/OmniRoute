package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentConfigDto(
        long latencyMs,
        double failureRate,
        Boolean demoEnabled
) {
    public PaymentConfigDto(long latencyMs, double failureRate) {
        this(latencyMs, failureRate, true);
    }
}
