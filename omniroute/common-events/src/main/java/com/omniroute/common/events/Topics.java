package com.omniroute.common.events;

public final class Topics {
    private Topics() {}

    public static final String ORDER_EVENTS = "order-events";
    public static final String WAREHOUSE_EVENTS = "warehouse-events";
    public static final String PAYMENT_EVENTS = "payment-events";
    public static final String WAREHOUSE_COMMANDS = "warehouse-commands";
    public static final String PAYMENT_COMMANDS = "payment-commands";
    public static final String FLEET_GPS = "fleet-gps";
    public static final String ROUTING_COMMANDS = "routing-commands";

    public static final String[] ALL_TOPICS = {
            ORDER_EVENTS,
            WAREHOUSE_EVENTS,
            PAYMENT_EVENTS,
            WAREHOUSE_COMMANDS,
            PAYMENT_COMMANDS,
            FLEET_GPS,
            ROUTING_COMMANDS
    };
}
