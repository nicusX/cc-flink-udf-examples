package io.confluent.flink.examples.ptf.statemachine.domain;

import org.apache.flink.table.annotation.DataTypeHint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * State of an Order entity.
 * <p>
 * Flink @DataTypeHint annotations are required to allow Flink serializing the state correctly.
 */
public class Order {

    public static class OrderItem {
        public String product;
        public int quantity;

        @DataTypeHint("DECIMAL(10, 4)")
        public BigDecimal unitPrice;
    }

    // This enum is used in the logic. Fields are STRING.
    // There is no SQL type mapping for Java enum
    public enum OrderStatus {
        CREATED,
        PAID,
        SHIPPED
    }

    public String orderId;
    public String customerId;
    public String customerName;
    public String deliveryAddress;
    public String trackingNumber;
    public String status;

    // List of items
    public OrderItem[] items = new OrderItem[0];

    // Timestamp when state changes happened
    @DataTypeHint("TIMESTAMP(3)")
    public LocalDateTime orderCreatedAt;
    @DataTypeHint("TIMESTAMP(3)")
    public LocalDateTime orderPaidAt;
    @DataTypeHint("TIMESTAMP(3)")
    public LocalDateTime orderShippedAt;
}
