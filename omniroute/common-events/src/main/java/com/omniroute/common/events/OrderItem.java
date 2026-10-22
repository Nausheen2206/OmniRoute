package com.omniroute.common.events;

import java.io.Serializable;

public record OrderItem(
        String sku,
        int quantity
) implements Serializable {}
