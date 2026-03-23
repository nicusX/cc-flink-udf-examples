package io.confluent.flink.examples.udf.table;

import org.apache.flink.table.api.ValidationException;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParseStructuredJsonTest {

    private final List<Row> collected = new ArrayList<>();
    private final ParseStructuredJson udf = new ParseStructuredJson();

    @BeforeEach
    void setUp() throws Exception {
        udf.open(null);
        udf.setCollector(new Collector<>() {
            @Override
            public void collect(Row row) {
                collected.add(row);
            }

            @Override
            public void close() {
            }
        });
        collected.clear();
    }

    @Test
    void evalParsesAllFieldsWithMultipleItems() {
        String json = """
                {
                  "order_id": "ORD-001",
                  "order_status": "DELIVERED",
                  "customer": {
                    "first_name": "Alice",
                    "middle_name": "Marie",
                    "last_name": "Smith"
                  },
                  "billing_address": {
                    "street_and_nr": "742 Evergreen Terrace",
                    "city": "Springfield",
                    "post_code": "62704",
                    "country": "US"
                  },
                  "shipping_address": {
                    "street_and_nr": "31 Spooner Street",
                    "city": "Quahog",
                    "post_code": "00093",
                    "country": "US"
                  },
                  "items": [
                    { "product_id": "SKU-A100", "unit_price": "29.99", "quantity": 2 },
                    { "product_id": "SKU-B200", "unit_price": "9.50", "quantity": 1 }
                  ],
                  "created_at": 1700000000000,
                  "shipped_at": 1700100000000,
                  "delivered_at": 1700200000000
                }
                """;

        udf.eval(json, true);

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);

        // order_id, order_status
        assertThat(row.getField(0)).isEqualTo("ORD-001");
        assertThat(row.getField(1)).isEqualTo("DELIVERED");

        // customer
        Row customer = (Row) row.getField(2);
        assertThat(customer.getField(0)).isEqualTo("Alice");
        assertThat(customer.getField(1)).isEqualTo("Marie");
        assertThat(customer.getField(2)).isEqualTo("Smith");

        // billing_address
        Row billing = (Row) row.getField(3);
        assertThat(billing.getField(0)).isEqualTo("742 Evergreen Terrace");
        assertThat(billing.getField(1)).isEqualTo("Springfield");
        assertThat(billing.getField(2)).isEqualTo("62704");
        assertThat(billing.getField(3)).isEqualTo("US");

        // shipping_address
        Row shipping = (Row) row.getField(4);
        assertThat(shipping.getField(0)).isEqualTo("31 Spooner Street");
        assertThat(shipping.getField(1)).isEqualTo("Quahog");

        // items
        Row[] items = (Row[]) row.getField(5);
        assertThat(items).hasSize(2);
        assertThat(items[0].getField(0)).isEqualTo("SKU-A100");
        assertThat(items[0].getField(1)).isEqualTo(new BigDecimal("29.99"));
        assertThat(items[0].getField(2)).isEqualTo(2);
        assertThat(items[1].getField(0)).isEqualTo("SKU-B200");
        assertThat(items[1].getField(1)).isEqualTo(new BigDecimal("9.50"));
        assertThat(items[1].getField(2)).isEqualTo(1);

        // timestamps
        assertThat(row.getField(6)).isEqualTo(1700000000000L);
        assertThat(row.getField(7)).isEqualTo(1700100000000L);
        assertThat(row.getField(8)).isEqualTo(1700200000000L);
    }

    @Test
    void evalHandlesNullBillingAddress() {
        String json = """
                {
                  "order_id": "ORD-002",
                  "order_status": "CREATED",
                  "customer": {
                    "first_name": "Bob",
                    "last_name": "Jones"
                  },
                  "shipping_address": {
                    "street_and_nr": "221B Baker Street",
                    "city": "London",
                    "post_code": "NW1 6XE",
                    "country": "GB"
                  },
                  "items": [
                    { "product_id": "SKU-C300", "unit_price": "15.00", "quantity": 3 }
                  ],
                  "created_at": 1700000000000
                }
                """;

        udf.eval(json, true);

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);

        assertThat(row.getField(0)).isEqualTo("ORD-002");

        // customer middle_name is null
        Row customer = (Row) row.getField(2);
        assertThat(customer.getField(1)).isNull();

        // billing_address is null
        assertThat(row.getField(3)).isNull();

        // shipping_address is present
        assertThat(row.getField(4)).isNotNull();

        // optional timestamps are null
        assertThat(row.getField(7)).isNull();
        assertThat(row.getField(8)).isNull();
    }

    @Test
    void evalHandlesEmptyItemsArray() {
        String json = """
                {
                  "order_id": "ORD-003",
                  "order_status": "PENDING",
                  "customer": { "first_name": "Carol", "last_name": "White" },
                  "shipping_address": { "street_and_nr": "1 Main St", "city": "Anytown", "post_code": "12345", "country": "US" },
                  "items": [],
                  "created_at": 1700000000000
                }
                """;

        udf.eval(json, true);

        assertThat(collected).hasSize(1);
        Row[] items = (Row[]) collected.get(0).getField(5);
        assertThat(items).isEmpty();
    }

    @Test
    void evalHandlesMissingItemsField() {
        String json = """
                {
                  "order_id": "ORD-004",
                  "order_status": "CREATED",
                  "customer": { "first_name": "Eve", "last_name": "Green" },
                  "created_at": 1700000000000
                }
                """;

        udf.eval(json, true);

        assertThat(collected).hasSize(1);
        Row[] items = (Row[]) collected.get(0).getField(5);
        assertThat(items).isEmpty();
    }

    @Test
    void evalMissingOrderIdFailOnErrorThrows() {
        String json = """
                {
                  "order_status": "CREATED",
                  "customer": { "first_name": "Dan", "last_name": "Brown" },
                  "shipping_address": { "street_and_nr": "1 Main St", "city": "Anytown", "post_code": "12345", "country": "US" },
                  "items": [],
                  "created_at": 1700000000000
                }
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: order_id");
    }

    @Test
    void evalMissingOrderIdNoFailOnErrorCollectsNothing() {
        String json = """
                {
                  "order_status": "CREATED",
                  "customer": { "first_name": "Dan", "last_name": "Brown" },
                  "items": [],
                  "created_at": 1700000000000
                }
                """;

        udf.eval(json, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void evalMissingOrderStatusThrows() {
        String json = """
                {"order_id":"ORD-001","customer":{"first_name":"Dan","last_name":"Brown"},"items":[],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: order_status");
    }

    @Test
    void evalMissingCustomerFirstNameThrows() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"last_name":"Brown"},"items":[],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: first_name");
    }

    @Test
    void evalMissingCustomerLastNameThrows() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"first_name":"Dan"},"items":[],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: last_name");
    }

    @Test
    void evalMissingAddressStreetThrows() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"shipping_address":{"city":"Anytown","post_code":"12345","country":"US"},"items":[],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: street_and_nr");
    }

    @Test
    void evalMissingAddressPostCodeThrows() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"billing_address":{"street_and_nr":"1 Main St","city":"Anytown","country":"US"},"items":[],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: post_code");
    }

    @Test
    void evalMissingItemProductIdThrows() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"items":[{"unit_price":"5.99","quantity":1}],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing required field: product_id");
    }

    @Test
    void evalInvalidUnitPriceThrows() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"items":[{"product_id":"SKU-A100","unit_price":"abc123","quantity":1}],"created_at":1700000000000}
                """;

        assertThatThrownBy(() -> udf.eval(json, true))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void evalInvalidUnitPriceNoFailOnErrorCollectsNothing() {
        String json = """
                {"order_id":"ORD-001","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"items":[{"product_id":"SKU-A100","unit_price":"abc123","quantity":1}],"created_at":1700000000000}
                """;

        udf.eval(json, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void evalMissingPostCodeInShippingAddressNoFailOnErrorCollectsNothing() {
        String json = """
                {"order_id":"ORD-2024-9999","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"shipping_address":{"street_and_nr":"1 Main St","city":"Anytown","country":"US"},"items":[{"product_id":"SKU-E500","unit_price":"5.99","quantity":1}],"created_at":1700000000000}
                """;

        udf.eval(json, false);

        assertThat(collected).isEmpty();
    }
}
