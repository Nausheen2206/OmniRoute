package com.omniroute.payment.api;

public record PaymentConfigDto(
        Double failureRate,
        Long latencyMs
) {}
