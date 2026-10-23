package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CircuitBreakerDto(
        String name,
        String state,
        Float failureRate,
        Float slowCallRate,
        Integer bufferedCalls,
        Integer failedCalls,
        Integer successfulCalls,
        Long notPermittedCalls
) {}
