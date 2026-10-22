package com.omniroute.common.events;

public final class EventTypes {
    private EventTypes() {}

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_ALLOCATED = "OrderAllocated";
    public static final String INVENTORY_LOCKED = "InventoryLocked";
    public static final String INVENTORY_LOCK_FAILED = "InventoryLockFailed";
    public static final String RELEASE_INVENTORY = "ReleaseInventory";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String PAYMENT_REQUESTED = "PaymentRequested";
    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_PAYMENT = "RefundPayment";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";
    public static final String TRUCK_POSITION_UPDATED = "TruckPositionUpdated";
    public static final String TRUCK_ASSIGNED = "TruckAssigned";
}
