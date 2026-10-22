package com.omniroute.common.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import java.time.Instant;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        defaultImpl = OrderCreated.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderCreated.class, name = "OrderCreated"),
        @JsonSubTypes.Type(value = OrderAllocated.class, name = "OrderAllocated"),
        @JsonSubTypes.Type(value = InventoryLocked.class, name = "InventoryLocked"),
        @JsonSubTypes.Type(value = InventoryLockFailed.class, name = "InventoryLockFailed"),
        @JsonSubTypes.Type(value = ReleaseInventory.class, name = "ReleaseInventory"),
        @JsonSubTypes.Type(value = InventoryReleased.class, name = "InventoryReleased"),
        @JsonSubTypes.Type(value = PaymentRequested.class, name = "PaymentRequested"),
        @JsonSubTypes.Type(value = PaymentCompleted.class, name = "PaymentCompleted"),
        @JsonSubTypes.Type(value = PaymentFailed.class, name = "PaymentFailed"),
        @JsonSubTypes.Type(value = RefundPayment.class, name = "RefundPayment"),
        @JsonSubTypes.Type(value = PaymentRefunded.class, name = "PaymentRefunded"),
        @JsonSubTypes.Type(value = TruckPositionUpdated.class, name = "TruckPositionUpdated"),
        @JsonSubTypes.Type(value = TruckAssigned.class, name = "TruckAssigned")
})
public interface OmniRouteEvent extends Serializable {
    String eventId();
    String orderId();
    Instant timestamp();
}
