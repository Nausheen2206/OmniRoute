package com.omniroute.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SagaStepDto(String step, String eventId, Instant createdAt) {
}
