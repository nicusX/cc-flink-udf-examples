package io.confluent.flink.examples.ptf.statemachine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.flink.examples.ptf.statemachine.domain.Order;
import io.confluent.flink.examples.ptf.statemachine.domain.Order.OrderStatus;
import io.confluent.flink.examples.ptf.statemachine.domain.OrderEvent;
import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.StateHint;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.ProcessTableFunction;
import org.apache.flink.types.Row;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.lang3.ArrayUtils;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static io.confluent.flink.examples.ptf.statemachine.domain.Order.OrderStatus.*;
import static org.apache.flink.table.annotation.ArgumentTrait.*;

/**
 * This PTF materializes the state of an entity (Orders) based on incoming events.
 * Each event only contains partial information about the entity. This PTF maintains the complete state.
 *
 * The input records contains 4 fields: 1/ orderId (String), 2/ timestamp (LONG), 3/ eventType (String), 4/ event payload (STRING).
 * The event payload is a JSON with the fields specific to that event type.
 *
 * When an event changes orderStatus, the entire order is emitted as a ROW. Order Items are represented as an ARRAY of ROWS
 */
// TODO add @FunctionHint(output = ...) with the structure of the emitted ROW containing the order
public class EntityStateMaterializer extends ProcessTableFunction<Row> {
    private static final Logger LOGGER = LogManager.getLogger(EntityStateMaterializer.class);

    private transient ObjectMapper mapper;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        mapper = new ObjectMapper();
    }

    public void eval(
            @StateHint(ttl="90 days") Order orderState,
            @ArgumentHint(ROW_SEMANTIC_TABLE) Row input) throws Exception {

        // Extract orderId, eventType, and timestamp
        // TODO make this robust, in case of parsing errors
        String orderId = input.getFieldAs("orderId");
        OrderEvent.EventType eventType = OrderEvent.EventType.valueOf(input.getFieldAs("eventType"));
        Instant eventTime = LocalDateTime.parse(input.getFieldAs("eventTime")).toInstant(ZoneOffset.UTC);
        String eventPayload = input.getFieldAs("eventPayload");

        // Based on eventType, parse the eventPayload field of the input Row and create the correct OrderEvent
        OrderEvent event = null;
        switch (eventType) {
            case CREATE:
                // Parse the CreateOrder event
                event = mapper.convertValue(eventPayload, OrderEvent.CreateOrder.class);
                break;
            case ADD_ITEM:
                event = mapper.convertValue(eventPayload, OrderEvent.AddItem.class);
                break;

            // TODO add other event types

        }


        // Implementation of the state machine
        boolean emitOutput = false;
        switch(eventType) {
            case CREATE:
                // Status validation
                if( orderState.status != null) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status.name() + "'");
                }

                // Process the CreateOrder event
                OrderEvent.CreateOrder createOrderEvent = (OrderEvent.CreateOrder)event;
                orderState.customerId = createOrderEvent.customerId;
                orderState.customerName = createOrderEvent.customerName;
                orderState.customerId = createOrderEvent.customerId;
                orderState.orderCreatedAt = eventTime;

                // Order status change
                orderState.status = CREATED;
                emitOutput = true;
            break;

            case ADD_ITEM:
                // Status validation
                if( orderState.status != CREATED) {
                    throw new RuntimeException("Invalid Order Event '" + eventType + "' for status '" + orderState.status.name() + "'");
                }

                // Process AddItem event
                OrderEvent.AddItem addItemEvent = (OrderEvent.AddItem)event;
                Order.OrderItem newItem = new Order.OrderItem();
                newItem.product =  addItemEvent.product;
                newItem.quantity = addItemEvent.quantity;
                newItem.unitPrice = addItemEvent.unitPrice;

                orderState.items = ArrayUtils.add(orderState.items, newItem);

                // No order status change. No output to emit
                emitOutput = false;
            break;


            // TODO handle other event types
        }

        // Conditionally emit the output
        if( emitOutput ) {
            // TODO emit a Row instance containing the order
        }
    }

}
