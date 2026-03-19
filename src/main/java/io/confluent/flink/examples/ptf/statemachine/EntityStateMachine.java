package io.confluent.flink.examples.ptf.statemachine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.flink.examples.ptf.statemachine.domain.Order;
import io.confluent.flink.examples.ptf.statemachine.domain.OrderEvent;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;

import static io.confluent.flink.examples.ptf.statemachine.domain.Order.OrderStatus.CREATED;
import static io.confluent.flink.examples.ptf.statemachine.domain.Order.OrderStatus.PAID;
import static io.confluent.flink.examples.ptf.statemachine.domain.Order.OrderStatus.SHIPPED;
import static org.apache.flink.table.annotation.ArgumentTrait.ROW_SEMANTIC_TABLE;
import static org.apache.flink.table.annotation.ArgumentTrait.SET_SEMANTIC_TABLE;

/**
 * This PTF materializes the state of an entity (Orders) based on incoming events.
 * Each event only contains partial information about the entity. This PTF maintains the complete state.
 * <p>
 * The input records contains 4 fields: 1/ orderId (String), 2/ eventTime (STRING - ISO datetime), 3/ eventType (String), 4/ event payload (STRING).
 * The event payload is a JSON with the fields specific to that event type.
 * <p>
 * When an event changes orderStatus, the entire order is emitted as a ROW. Order Items are represented as an ARRAY of ROWS
 */
// Output row schema - note that orderId is automatically passed through as partition key
@DataTypeHint("ROW<customerId STRING, customerName STRING, deliveryAddress STRING, trackingNumber STRING, status STRING, items ARRAY<ROW<product STRING, quantity INT, unitPrice DECIMAL(10, 2)>>, orderCreatedAt TIMESTAMP(3), orderPaidAt TIMESTAMP(3), orderShippedAt TIMESTAMP(3)>")
public class EntityStateMachine extends ProcessTableFunction<Row> {
    private static final Logger LOG = LogManager.getLogger(EntityStateMachine.class);

    private transient ObjectMapper mapper;

    @Override
    public void open(FunctionContext context) throws Exception {
        // Initialize the mapper only once
        mapper = new ObjectMapper();
    }

    public void eval(
            @StateHint(ttl = "90 days") Order orderState,
            @ArgumentHint({SET_SEMANTIC_TABLE}) Row input) throws Exception {

        // Extract orderId, eventType, and timestamp
        String orderId = input.getFieldAs("orderId");
        OrderEvent.EventType eventType = OrderEvent.EventType.valueOf(input.getFieldAs("eventType"));
        LocalDateTime eventTime = LocalDateTime.parse(input.getFieldAs("eventTime"));
        String eventPayload = input.getFieldAs("eventPayload");

        LOG.info("Processing event {} for order {} at {}", eventType, orderId, eventTime);

        // Based on eventType, parse the eventPayload field of the input Row and create the correct OrderEvent
        OrderEvent event = null;
        switch (eventType) {
            case CREATE:
                // Parse the CreateOrder event
                event = mapper.readValue(eventPayload, OrderEvent.CreateOrder.class);
                break;
            case ADD_ITEM:
                event = mapper.readValue(eventPayload, OrderEvent.AddItem.class);
                break;
            case UPDATE_ADDRESS:
                event = mapper.readValue(eventPayload, OrderEvent.UpdateAddress.class);
                break;
            case PAY:
                event = mapper.readValue(eventPayload, OrderEvent.PayOrder.class);
                break;
            case SHIP:
                event = mapper.readValue(eventPayload, OrderEvent.ShipOrder.class);
                break;
        }


        // Implementation of the state machine
        boolean emitOutput = false;
        switch (eventType) {
            case CREATE:
                // Status validation
                if (orderState.status != null) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // Process the CreateOrder event
                OrderEvent.CreateOrder createOrderEvent = (OrderEvent.CreateOrder) event;
                if (!orderId.equals(createOrderEvent.orderId)) {
                    throw new RuntimeException("Inconsistent OrderId");
                }

                orderState.orderId = createOrderEvent.orderId;
                orderState.customerId = createOrderEvent.customerId;
                orderState.customerName = createOrderEvent.customerName;
                orderState.orderCreatedAt = eventTime;


                // Order status change
                orderState.status = CREATED.name();
                LOG.info("Order {} status changed to {} at {}", orderId, orderState.status, eventTime);
                emitOutput = true;
                break;

            case ADD_ITEM:
                // Status validation
                if (!CREATED.name().equals(orderState.status)) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // Process AddItem event
                OrderEvent.AddItem addItemEvent = (OrderEvent.AddItem) event;
                Order.OrderItem newItem = new Order.OrderItem();
                newItem.product = addItemEvent.product;
                newItem.quantity = addItemEvent.quantity;
                newItem.unitPrice = addItemEvent.unitPrice;

                orderState.items = ArrayUtils.add(orderState.items, newItem);

                // No order status change. No output to emit
                emitOutput = false;
                break;


            case UPDATE_ADDRESS:
                // Status validation
                if (!CREATED.name().equals(orderState.status)) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // Process UpdateAddress event
                OrderEvent.UpdateAddress updateAddressEvent = (OrderEvent.UpdateAddress) event;
                orderState.deliveryAddress = updateAddressEvent.deliveryAddress;

                // No order status change. No output to emit
                emitOutput = false;
                break;

            case PAY:
                // Status validation
                if (!CREATED.name().equals(orderState.status)) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // No fields to extract from PayOrder

                // Order status change
                orderState.status = PAID.name();
                orderState.orderPaidAt = eventTime;
                LOG.info("Order {} status changed to {} at {}", orderId, orderState.status, eventTime);
                emitOutput = true;
                break;

            case SHIP:
                // Status validation
                if (!PAID.name().equals(orderState.status)) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // Process ShipOrder event
                OrderEvent.ShipOrder shipOrderEvent = (OrderEvent.ShipOrder) event;
                orderState.trackingNumber = shipOrderEvent.trackingNumber;

                // Order status change
                orderState.status = SHIPPED.name();
                orderState.orderShippedAt = eventTime;
                LOG.info("Order {} status changed to {} at {}", orderId, orderState.status, eventTime);
                emitOutput = true;
                break;
        }

        // Conditionally emit the output
        if (emitOutput) {
            Row[] itemRows = new Row[orderState.items.length];
            for (int i = 0; i < orderState.items.length; i++) {
                Order.OrderItem oi = orderState.items[i];
                itemRows[i] = Row.of(oi.product, oi.quantity, oi.unitPrice);
            }

            collect(Row.of(
                    orderState.customerId,
                    orderState.customerName,
                    orderState.deliveryAddress,
                    orderState.trackingNumber,
                    orderState.status,
                    itemRows,
                    orderState.orderCreatedAt,
                    orderState.orderPaidAt,
                    orderState.orderShippedAt
            ));
        }
    }

}
