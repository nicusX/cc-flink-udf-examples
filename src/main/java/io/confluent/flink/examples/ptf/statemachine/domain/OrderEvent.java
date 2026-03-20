package io.confluent.flink.examples.ptf.statemachine.domain;

import org.apache.flink.table.annotation.DataTypeHint;

import java.math.BigDecimal;

/**
 * Order event payload, represented as JSON in the event record.
 *
 * This class is not serialized by Flink directly. The PTF use it to deserialize the JSON event payload using Jackson 2
 * ObjectMapper.
 */
public class OrderEvent {

    public enum EventType {
        CREATE,
        UPDATE_ADDRESS,
        ADD_ITEM,
        PAY,
        SHIP
    }

    public static class CreateOrder extends OrderEvent {
        public String orderId;
        public String customerId;
        public String customerName;
    }

    public static class UpdateAddress extends OrderEvent {
        public String deliveryAddress;
    }

    public static class AddItem extends OrderEvent {
        public String product;
        public int quantity;
        public BigDecimal unitPrice;
    }

    public static class PayOrder extends OrderEvent {
    }

    public static class ShipOrder extends OrderEvent {
        public String trackingNumber;
    }
}
