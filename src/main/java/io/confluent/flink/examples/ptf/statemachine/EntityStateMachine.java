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
 * This PTF implements an Event Sourcing pattern, materializing the state of an entity (Orders) based on incoming events.
 * <p>
 * Events only contain partial information about the entity. This PTF maintains the complete state of each entity in Flink state.
 * <p>
 * The input records (Events) contain 4 fields: 1/ orderId (String), 2/ eventTime (STRING - ISO datetime), 3/ eventType (String), 4/ event payload (STRING).
 * <p>
 * <p>
 * There are different types of events, each with a specific payload. To represent these polymorphic events, the payload
 * is JSON in a single  STRING field of the input record.
 * <p>
 * The PTF emits a ROW representing the full state of an entity only when orderStatus changes.
 * The output record is structured: the items field contains an ARRAY of ROWs, each representing an order item.
 * <p>
 * The PTF invocation must be partitioned by orderId. For example:
 * <code>
 * INSERT INTO orders
 * SELECT *
 * FROM EntityStateMachine(
 * TABLE order_events
 * PARTITION BY orderId
 * );
 * </code>
 * <p>
 * In this PTF we are logging on every processed record. In a real world, production scenario it is not advisable to log
 * at INFO or higher on every single record.
 */
// Output row schema - note that orderId is automatically passed through as partition key
@DataTypeHint("ROW<customerId STRING, customerName STRING, deliveryAddress STRING, trackingNumber STRING, status STRING, items ARRAY<ROW<product STRING, quantity INT, unitPrice DECIMAL(10, 2)>>, orderCreatedAt TIMESTAMP(3), orderPaidAt TIMESTAMP(3), orderShippedAt TIMESTAMP(3)>")
public class EntityStateMachine extends ProcessTableFunction<Row> {
    private static final Logger LOG = LogManager.getLogger(EntityStateMachine.class);

    /**
     * Jackson Object Mapper.
     * Marked as transient, to prevent operator initialization errors. Initialized in the open() method, when the operator is initialized.
     */
    private transient ObjectMapper mapper;

    /**
     * Initializes "expensive" resources only once
     */
    @Override
    public void open(FunctionContext context) throws Exception {
        // Initialize the mapper only once
        mapper = new ObjectMapper();
    }

    /**
     * Event-handler logic.
     *
     * @param orderState state of a specific partition (a specific orderId).
     *                   When a given orderId is observed for the first time, the Order is initialized using the default (no-arg) constructor and passed to the function.
     *                   Any mutation to orderState is preserved, until the state TTL expires (90 days in this case, see @StateHint annotation).
     * @param input      input ROW containing the order events.
     */
    public void eval(
            // Specify the TTL for each order.
            @StateHint(ttl = "90 days") Order orderState,
            // Input ROW. The schema if the input ROW is implied by the PTF code and not declared explicitly
            @ArgumentHint({SET_SEMANTIC_TABLE}) Row input) throws Exception {

        // Extract the main field from the input ROW containing the event envelope
        String orderId = input.getFieldAs("orderId");
        OrderEvent.EventType eventType = OrderEvent.EventType.valueOf(input.getFieldAs("eventType"));
        LocalDateTime eventTime = LocalDateTime.parse(input.getFieldAs("eventTime"));
        String eventPayload = input.getFieldAs("eventPayload");

        LOG.info("Processing event {} for order {} at {}", eventType, orderId, eventTime);

        // Parse the polymorphic event,
        // Based on eventType, parse the JSON event payload into different OrderEvent types
        OrderEvent event = switch (eventType) {
            case CREATE -> mapper.readValue(eventPayload, OrderEvent.CreateOrder.class);
            case ADD_ITEM -> mapper.readValue(eventPayload, OrderEvent.AddItem.class);
            case UPDATE_ADDRESS -> mapper.readValue(eventPayload, OrderEvent.UpdateAddress.class);
            case PAY -> mapper.readValue(eventPayload, OrderEvent.PayOrder.class);
            case SHIP -> mapper.readValue(eventPayload, OrderEvent.ShipOrder.class);
        };

        /// Business logic of the state machine

        // Update the entity state based on the event
        // Decide whether the PTF should emit the output
        boolean emitOutput = false;
        switch (eventType) {
            case CREATE:
                // Simple status validation
                // Note that, if status validation fails, the PTF throws an exception and the Flink SQL statement stops.
                // In a real world scenario you may want to implement a more refined error handling approach.
                if (orderState.status != null) {
                    LOG.error("Invalid Order Event '{}' for status '{}'", eventType, orderState.status);
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // Apply the CreateOrder event
                OrderEvent.CreateOrder createOrderEvent = (OrderEvent.CreateOrder) event;
                if (!orderId.equals(createOrderEvent.orderId)) {
                    // This can happen if you wrongly partitioned the PTF invocation
                    LOG.error("Inconsistent orderId. Partition:'{}', event orderId: '{}'. Have you partitioned the PTF invocation by orderId?", orderId, createOrderEvent.orderId);
                    throw new RuntimeException("Inconsistent OrderId");
                }
                orderState.orderId = createOrderEvent.orderId;
                orderState.customerId = createOrderEvent.customerId;
                orderState.customerName = createOrderEvent.customerName;
                orderState.orderCreatedAt = eventTime;


                // Order status change
                orderState.status = CREATED.name();
                LOG.info("Order {} status changed to {} at {}", orderId, orderState.status, eventTime);
                // Because the order status changed, the function must emit the full order
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

                // No order status change. No output to emit in this case
                emitOutput = false;
                break;


            case UPDATE_ADDRESS:
                // Status validation
                if (!CREATED.name().equals(orderState.status)) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status + "'");
                }

                // Apply the UpdateAddress event
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

                // Apply the PayOrder event
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

                // Apply the ShipOrder event
                OrderEvent.ShipOrder shipOrderEvent = (OrderEvent.ShipOrder) event;
                orderState.trackingNumber = shipOrderEvent.trackingNumber;

                // Order status change
                orderState.status = SHIPPED.name();
                orderState.orderShippedAt = eventTime;
                LOG.info("Order {} status changed to {} at {}", orderId, orderState.status, eventTime);
                emitOutput = true;
                break;

            default:
                LOG.error("Invalid Order status '{}'", orderState.status);
                throw new RuntimeException("Invalid Order status '" + orderState.status + "'");
        }

        // Conditionally emit the output
        if (emitOutput) {
            // Build nested Order Items
            Row[] itemRows = new Row[orderState.items.length];
            for (int i = 0; i < orderState.items.length; i++) {
                Order.OrderItem oi = orderState.items[i];
                itemRows[i] = Row.of(oi.product, oi.quantity, oi.unitPrice);
            }

            // Emit a single row representing the entire Order
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
