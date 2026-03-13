package io.confluent.flink.examples.ptf.statemachine.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represent the state of an Order.
 *
 */
public class Order {

    public static class OrderItem {
        public String product;
        public int quantity;
        public BigDecimal unitPrice;
    }

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
    public OrderStatus status;

    // List of items
    public OrderItem[] items = new OrderItem[0];

    // Timestamp when state changes happened
    public Instant orderCreatedAt;

    public Instant orderPaidAt;
    public Instant orderShippedAt;

}
