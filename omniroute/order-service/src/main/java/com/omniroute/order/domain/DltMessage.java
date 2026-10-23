package com.omniroute.order.domain;

import java.time.Instant;

public record DltMessage(
        String id,
        String topic,
        String originalTopic,
        String key,
        String payload,
        String error,
        Instant timestamp
) {}
