# Process Table Function (PTF) - Entity State Machine

> ⚠️ Make sure in your SQL Workspace you select the Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.


This Process Table Function demonstrates the implementation of a simple state machine managing the state of orders,
based on input events, and emitting the full order state only when the status of the order changes.

**Source code:**
- [EntityStateMachine](../src/main/java/io/confluent/flink/examples/ptf/statemachine/EntityStateMachine.java) — PTF implementation
- [Order](../src/main/java/io/confluent/flink/examples/ptf/statemachine/domain/Order.java) — state object
- [OrderEvent](../src/main/java/io/confluent/flink/examples/ptf/statemachine/domain/OrderEvent.java) — event payload
- [EntityStateMachineTest](../src/test/java/io/confluent/flink/examples/ptf/statemachine/EntityStateMachineTest.java) — unit tests


## Order State Machine Logic

### Input: Order Events

As input, the PTF expects a table containing different types of order events. To represent polymorphic events,
the input table uses an envelope pattern where the event payload is a single STRING field containing JSON.
The `order_events` table has the following fields:
* orderId - STRING
* eventTime - STRING (ISO datetime)
* eventType - STRING ("CREATE", "UPDATE_ADDRESS", "ADD_ITEM", "PAY", "SHIP")
* eventPayload - STRING, containing the JSON payload of the event (content varies by event type)

**CREATE** — creates a new order
```json
{
  "orderId": "order-1",
  "customerId": "cust-1",
  "customerName": "Alice"
}
```

**ADD_ITEM** — adds an item to the order (only valid in CREATED status)
```json
{
  "product": "Widget",
  "quantity": 2,
  "unitPrice": 5.00
}
```

**UPDATE_ADDRESS** — sets the delivery address (only valid in CREATED status)
```json
{
  "deliveryAddress": "456 Oak Ave"
}
```

**PAY** — marks the order as paid (only valid in CREATED status)
```json
{}
```

**SHIP** — marks the order as shipped with a tracking number (only valid in PAID status)
```json
{
  "trackingNumber": "TRACK-456"
}
```

### State Machine Logic

The state machine implements simple business logic: events are applied to each order, and the PTF maintains the full state.

> ⚠️ The state of a PTF is related to a single *partition*, defined by `PARTITION BY` at the PTF invocation.
> In this case the processing is partitioned by `orderId` and a single PTF state object represents the state of a single Order.

### Output

The complete order is emitted only under specific conditions. In this example, when an event changes the order's *status*. In a real scenario, the business logic can be more complex.

The PTF emits a single ROW representing the Order into an append-only table.

The `items` field contains an `ARRAY` of `ROW`s, each representing an order item.

---

## Interactively Testing the EntityStateMachine PTF


### 1. Register the PTF

```sql
CREATE FUNCTION `EntityStateMachine`
    AS 'io.confluent.flink.examples.ptf.statemachine.EntityStateMachine'
    USING JAR 'confluent-artifact://<artifact-id>'
```

### 2. Create the source table

```sql
CREATE TABLE order_events (
    orderId STRING,
    eventType STRING,
    eventTime STRING,
    eventPayload STRING
) WITH (
    'kafka.consumer.isolation-level'='read-uncommitted',
    'scan.startup.mode'='latest-offset'
);
```

> ℹ️ The option `'kafka.consumer.isolation-level'='read-uncommitted'` ensures records are output immediately. This is not required for the PTF.

### 3. Create the destination table

```sql
CREATE TABLE orders (
    orderId STRING,
    customerId STRING,
    customerName STRING,
    deliveryAddress STRING,
    trackingNumber STRING,
    `status` STRING,
    items ARRAY<ROW<product STRING, quantity INT, unitPrice DECIMAL(10, 2)>>,
    orderCreatedAt TIMESTAMP(3),
    orderPaidAt TIMESTAMP(3),
    orderShippedAt TIMESTAMP(3)
) WITH (
    'kafka.consumer.isolation-level'='read-uncommitted',
    'scan.startup.mode'='latest-offset'
);
```

### 4. Start the PTF pipeline

```sql
INSERT INTO orders
SELECT *
FROM EntityStateMachine(
    TABLE order_events
    PARTITION BY orderId
);
```

### 5. Observe the PTF output

In a separate SQL Workspace tab, run:

```sql
SELECT * FROM orders;
```

### 6. Insert sample events

> ℹ️ Run step 6 in a separate SQL Workspace tab to observe the output as you insert events.



#### Order A — full lifecycle (CREATED -> PAID -> SHIPPED)

```sql
INSERT INTO order_events VALUES
    ('order-1', 'CREATE', '2024-01-15T10:00:00',
     '{"orderId":"order-1","customerId":"cust-1","customerName":"Alice"}');
-- A new Order in status CREATED should be emitted

INSERT INTO order_events VALUES
    ('order-1', 'ADD_ITEM', '2024-01-15T10:05:00',
     '{"product":"Widget","quantity":2,"unitPrice":5.00}');
-- No output emitted (no status change)

INSERT INTO order_events VALUES
    ('order-1', 'ADD_ITEM', '2024-01-15T10:06:00',
     '{"product":"Gadget","quantity":1,"unitPrice":15.50}');
-- No output emitted (no status change)

INSERT INTO order_events VALUES
    ('order-1', 'UPDATE_ADDRESS', '2024-01-15T10:10:00',
     '{"deliveryAddress":"456 Oak Ave"}');
-- No output emitted (no status change)

INSERT INTO order_events VALUES
    ('order-1', 'PAY', '2024-01-15T11:00:00', '{}');
-- An order with status PAID, two items, and a delivery address should be emitted

INSERT INTO order_events VALUES
    ('order-1', 'SHIP', '2024-01-16T09:00:00',
     '{"trackingNumber":"TRACK-456"}');
-- An order with status SHIPPED and with a tracking nr should be emitted

```


#### Order B — partial lifecycle (CREATED -> PAID)

```sql
INSERT INTO order_events VALUES
    ('order-2', 'CREATE', '2024-01-15T14:00:00',
     '{"orderId":"order-2","customerId":"cust-2","customerName":"Bob"}');
-- A new Order in status CREATED should be emitted

INSERT INTO order_events VALUES
    ('order-2', 'ADD_ITEM', '2024-01-15T14:05:00',
     '{"product":"Sprocket","quantity":4,"unitPrice":3.25}');
-- No output emitted (no status change)

INSERT INTO order_events VALUES
    ('order-2', 'PAY', '2024-01-15T15:00:00', '{}');
-- An order with status PAID should be emitted
```
