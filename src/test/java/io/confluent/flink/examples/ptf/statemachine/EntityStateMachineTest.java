package io.confluent.flink.examples.ptf.statemachine;

import io.confluent.flink.examples.ptf.statemachine.domain.Order;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityStateMachineTest {

    private final List<Row> collected = new ArrayList<>();
    private final EntityStateMachine ptf = new EntityStateMachine();
    private Order orderState;

    @BeforeEach
    void setUp() throws Exception {
        ptf.open(null);
        ptf.setCollector(new Collector<>() {
            @Override
            public void collect(Row row) {
                collected.add(row);
            }

            @Override
            public void close() {
            }
        });
        collected.clear();
        orderState = new Order();
    }

    @Test
    void createOrderSetsStateAndEmitsRow() throws Exception {
        ptf.eval(orderState, inputRow("order-1", "CREATE", "2024-01-15T10:00:00",
                """
                {"customerId":"cust-1","customerName":"Alice"}
                """));

        assertThat(orderState.orderId).isEqualTo("order-1");
        assertThat(orderState.status).isEqualTo("CREATED");
        assertThat(orderState.customerId).isEqualTo("cust-1");
        assertThat(orderState.customerName).isEqualTo("Alice");
        assertThat(orderState.orderCreatedAt).isEqualTo(LocalDateTime.parse("2024-01-15T10:00:00"));

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);
        assertThat(row.getField(0)).isEqualTo("cust-1");
        assertThat(row.getField(1)).isEqualTo("Alice");
        assertThat(row.getField(4)).isEqualTo("CREATED");
    }

    @Test
    void addItemUpdatesStateWithoutEmitting() throws Exception {
        applyCreate();
        collected.clear();

        ptf.eval(orderState, inputRow("order-1", "ADD_ITEM", "2024-01-15T10:05:00",
                """
                {"product":"Widget","quantity":3,"unitPrice":9.99}
                """));

        assertThat(orderState.items).hasSize(1);
        assertThat(orderState.items[0].product).isEqualTo("Widget");
        assertThat(orderState.items[0].quantity).isEqualTo(3);
        assertThat(orderState.items[0].unitPrice).isEqualByComparingTo(new BigDecimal("9.99"));
        assertThat(orderState.totalPrice).isEqualByComparingTo(new BigDecimal("29.97"));
        assertThat(orderState.status).isEqualTo("CREATED");

        assertThat(collected).isEmpty();
    }

    @Test
    void updateAddressSetsAddressWithoutEmitting() throws Exception {
        applyCreate();
        collected.clear();

        ptf.eval(orderState, inputRow("order-1", "UPDATE_ADDRESS", "2024-01-15T10:10:00",
                """
                {"deliveryAddress":"123 Main St"}
                """));

        assertThat(orderState.deliveryAddress).isEqualTo("123 Main St");
        assertThat(orderState.status).isEqualTo("CREATED");

        assertThat(collected).isEmpty();
    }

    @Test
    void payOrderSetsStatusAndEmitsRow() throws Exception {
        applyCreate();
        collected.clear();

        ptf.eval(orderState, inputRow("order-1", "PAY", "2024-01-15T11:00:00",
                "{}"));

        assertThat(orderState.status).isEqualTo("PAID");
        assertThat(orderState.orderPaidAt).isEqualTo(LocalDateTime.parse("2024-01-15T11:00:00"));

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);
        assertThat(row.getField(4)).isEqualTo("PAID");
    }

    @Test
    void shipOrderSetsStatusAndTrackingAndEmitsRow() throws Exception {
        applyCreate();
        applyAddItem("Gadget", 1, "19.99");
        applyPay();
        collected.clear();

        ptf.eval(orderState, inputRow("order-1", "SHIP", "2024-01-16T09:00:00",
                """
                {"trackingNumber":"TRACK-123"}
                """));

        assertThat(orderState.status).isEqualTo("SHIPPED");
        assertThat(orderState.trackingNumber).isEqualTo("TRACK-123");
        assertThat(orderState.orderShippedAt).isEqualTo(LocalDateTime.parse("2024-01-16T09:00:00"));

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);
        assertThat(row.getField(3)).isEqualTo("TRACK-123");
        assertThat(row.getField(4)).isEqualTo("SHIPPED");

        Row[] items = (Row[]) row.getField(5);
        assertThat(items).hasSize(1);
        assertThat(items[0].getField(0)).isEqualTo("Gadget");
        assertThat(items[0].getField(1)).isEqualTo(1);
        assertThat(row.getField(6)).isEqualTo(new BigDecimal("19.99"));
    }

    @Test
    void fullLifecycleEmitsThreeRows() throws Exception {
        ptf.eval(orderState, inputRow("order-1", "CREATE", "2024-01-15T10:00:00",
                """
                {"customerId":"cust-1","customerName":"Alice"}
                """));
        ptf.eval(orderState, inputRow("order-1", "ADD_ITEM", "2024-01-15T10:05:00",
                """
                {"product":"Widget","quantity":2,"unitPrice":5.00}
                """));
        ptf.eval(orderState, inputRow("order-1", "ADD_ITEM", "2024-01-15T10:06:00",
                """
                {"product":"Gadget","quantity":1,"unitPrice":15.50}
                """));
        ptf.eval(orderState, inputRow("order-1", "UPDATE_ADDRESS", "2024-01-15T10:10:00",
                """
                {"deliveryAddress":"456 Oak Ave"}
                """));
        ptf.eval(orderState, inputRow("order-1", "PAY", "2024-01-15T11:00:00",
                "{}"));
        ptf.eval(orderState, inputRow("order-1", "SHIP", "2024-01-16T09:00:00",
                """
                {"trackingNumber":"TRACK-456"}
                """));

        // 3 status-changing events: CREATE, PAY, SHIP
        assertThat(collected).hasSize(3);

        // Verify final state
        assertThat(orderState.status).isEqualTo("SHIPPED");
        assertThat(orderState.items).hasSize(2);
        assertThat(orderState.deliveryAddress).isEqualTo("456 Oak Ave");
        assertThat(orderState.trackingNumber).isEqualTo("TRACK-456");

        // Verify last emitted Row has all accumulated data
        Row lastRow = collected.get(2);
        assertThat(lastRow.getField(2)).isEqualTo("456 Oak Ave");
        assertThat(lastRow.getField(3)).isEqualTo("TRACK-456");
        assertThat(lastRow.getField(4)).isEqualTo("SHIPPED");

        Row[] items = (Row[]) lastRow.getField(5);
        assertThat(items).hasSize(2);
        assertThat(items[0].getField(0)).isEqualTo("Widget");
        assertThat(items[1].getField(0)).isEqualTo("Gadget");
        // totalPrice = (2 * 5.00) + (1 * 15.50) = 25.50
        assertThat(lastRow.getField(6)).isEqualTo(new BigDecimal("25.50"));
    }

    @Test
    void createOnExistingOrderThrows() throws Exception {
        applyCreate();

        assertThatThrownBy(() -> ptf.eval(orderState, inputRow("order-1", "CREATE", "2024-01-15T12:00:00",
                """
                {"customerId":"cust-2","customerName":"Bob"}
                """)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid Order Event");
    }

    @Test
    void payOnAlreadyPaidOrderThrows() throws Exception {
        applyCreate();
        applyPay();

        assertThatThrownBy(() -> ptf.eval(orderState, inputRow("order-1", "PAY", "2024-01-15T13:00:00",
                "{}")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid Order Event");
    }

    // --- Helper methods ---

    private void applyCreate() throws Exception {
        ptf.eval(orderState, inputRow("order-1", "CREATE", "2024-01-15T10:00:00",
                """
                {"customerId":"cust-1","customerName":"Alice"}
                """));
    }

    private void applyAddItem(String product, int quantity, String unitPrice) throws Exception {
        ptf.eval(orderState, inputRow("order-1", "ADD_ITEM", "2024-01-15T10:05:00",
                String.format("""
                {"product":"%s","quantity":%d,"unitPrice":%s}
                """, product, quantity, unitPrice)));
    }

    private void applyPay() throws Exception {
        ptf.eval(orderState, inputRow("order-1", "PAY", "2024-01-15T11:00:00",
                "{}"));
    }

    private Row inputRow(String orderId, String eventType, String eventTime, String eventPayload) {
        Row row = Row.withNames();
        row.setField("orderId", orderId);
        row.setField("eventType", eventType);
        row.setField("eventTime", eventTime);
        row.setField("eventPayload", eventPayload);
        return row;
    }
}
