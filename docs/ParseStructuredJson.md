# Parse Structured JSON record without schema

User Defined Table Function (UDTF) source code:
[ParseStructuredJson](../src/main/java/io/confluent/flink/examples/udf/table/ParseStructuredJson.java)

This example shows how to use a User Defined Table Function (UDTF) to parse a message containing structured JSON
without a provided schema. The record is converted into a ROW containing structured objects.

> ⚠️ This technique allows extracting a JSON payload from a record without the corresponding schema in Schema Registry.
> However, the code of the UDF must have knowledge of the structure of the JSON to extract the fields.


### Input records

The UDTF expects a STRING (VARCHAR) as input, containing a JSON similar to the following:

**Full payload** — all fields populated, multiple items, both addresses:

```json
{
  "order_id": "ORD-2024-1001",
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
```

**Minimal payload** — no billing address, no shipping/delivery timestamps, single item:

```json
{
  "order_id": "ORD-2024-1002",
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
```

### Output records

The parsed order emitted by the function follows this schema (note the nested ROWs and ARRAYs):

```sql
  `order_id`          STRING NOT NULL,
  `order_status`      STRING NOT NULL,
  `customer`          ROW<
                        `first_name`  STRING NOT NULL,
                        `middle_name` STRING,
                        `last_name`   STRING NOT NULL
                      > NOT NULL,
  `billing_address`   ROW<
                        `street_and_nr` STRING NOT NULL,
                        `city`          STRING NOT NULL,
                        `post_code`     STRING NOT NULL,
                        `country`       STRING NOT NULL
                      >,
  `shipping_address`  ROW<
                        `street_and_nr` STRING NOT NULL,
                        `city`          STRING NOT NULL,
                        `post_code`     STRING NOT NULL,
                        `country`       STRING NOT NULL
                      >,
  `items`             ARRAY<ROW<
                        `product_id` STRING NOT NULL,
                        `unit_price` DECIMAL(9,2) NOT NULL,
                        `quantity`   INT NOT NULL
                      >> NOT NULL,
  `created_at`        BIGINT NOT NULL,
  `shipped_at`        BIGINT,
  `delivered_at`      BIGINT
```

---

## Testing the User Defined Table Function

> ⚠️ Make sure in your SQL Workspace you select the catalog and database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

### Register the UDTF

Register the function. Replace `<artifact-id>` with the ID of the JAR artifact you uploaded.
The artifact ID is a string starting with `cfa-` like `cfa-abc1234`.

```sql
CREATE FUNCTION `parse_json_order`
  AS 'io.confluent.flink.examples.udf.table.ParseStructuredJson'
  USING JAR 'confluent-artifact://<artifact-id>'
```


### Create source and destination tables

```sql
CREATE TABLE orders_json (
  `key` STRING NOT NULL,
  `val` STRING,
  PRIMARY KEY (`key`) NOT ENFORCED
) DISTRIBUTED BY (`key`) INTO 6 BUCKETS
WITH (
    'changelog.mode'='append',
    'scan.startup.mode'='latest-offset',
    'kafka.consumer.isolation-level'='read-uncommitted',
    'key.format'='raw',
    'value.format'='raw'
);
```

```sql
CREATE TABLE orders_parsed (
  `order_id` STRING NOT NULL,
  `order_status` STRING NOT NULL,
  `customer` ROW<`first_name` STRING NOT NULL, `middle_name` STRING, `last_name` STRING NOT NULL> NOT NULL,
  `billing_address` ROW<`street_and_nr` STRING NOT NULL, `city` STRING NOT NULL, `post_code` STRING NOT NULL, `country` STRING NOT NULL>,
  `shipping_address` ROW<`street_and_nr` STRING NOT NULL, `city` STRING NOT NULL, `post_code` STRING NOT NULL, `country` STRING NOT NULL>,
  `items` ARRAY<ROW<`product_id` STRING NOT NULL, `unit_price` DECIMAL(9,2) NOT NULL, `quantity` INT NOT NULL>> NOT NULL,
  `created_at` BIGINT NOT NULL,
  `shipped_at` BIGINT,
  `delivered_at` BIGINT
) DISTRIBUTED BY (`order_id`) INTO 6 BUCKETS
WITH (
    'changelog.mode'='append',
    'scan.startup.mode'='latest-offset',
    'kafka.consumer.isolation-level'='read-uncommitted',
    'key.format'='avro-registry',
    'value.format'='avro-registry'
);
```

### Run the parsing pipeline

```sql
INSERT INTO orders_parsed
SELECT 
    p.*
FROM orders_json
CROSS JOIN LATERAL TABLE(parse_json_order(`val`, FALSE)) AS p 
```

Keep this statement running.

> ⚠️ Error handling: the UDTF is called with the second argument `failOnError` = `FALSE`. When an error is encountered
> parsing the JSON payload, the processing does not stop and a WARN message is logged. The function also returns no record
> in case of error.

### Visualize output

In a separate panel, visualize the output table.

```sql
SELECT * 
FROM `orders_parsed`;
```

### Send input records

Send different payloads to the `orders_json` table and observe the output.
Run each `INSERT INTO` statement separately, one by one, and observe the results.

1. A delivered order with all fields, two items, and both addresses:

```sql
INSERT INTO orders_json VALUES (
  'ORD-2024-1001',
  '{"order_id":"ORD-2024-1001","order_status":"DELIVERED","customer":{"first_name":"Alice","middle_name":"Marie","last_name":"Smith"},"billing_address":{"street_and_nr":"742 Evergreen Terrace","city":"Springfield","post_code":"62704","country":"US"},"shipping_address":{"street_and_nr":"31 Spooner Street","city":"Quahog","post_code":"00093","country":"US"},"items":[{"product_id":"SKU-A100","unit_price":"29.99","quantity":2},{"product_id":"SKU-B200","unit_price":"9.50","quantity":1}],"created_at":1700000000000,"shipped_at":1700100000000,"delivered_at":1700200000000}'
)
```

2. A newly created order with no billing address and no shipping/delivery timestamps:

```sql
INSERT INTO orders_json VALUES (
  'ORD-2024-1002',
  '{"order_id":"ORD-2024-1002","order_status":"CREATED","customer":{"first_name":"Bob","last_name":"Jones"},"shipping_address":{"street_and_nr":"221B Baker Street","city":"London","post_code":"NW1 6XE","country":"GB"},"items":[{"product_id":"SKU-C300","unit_price":"15.00","quantity":3}],"created_at":1700000000000}'
)
```

3. A shipped order with a single item:

```sql
INSERT INTO orders_json VALUES (
  'ORD-2024-1003',
  '{"order_id":"ORD-2024-1003","order_status":"SHIPPED","customer":{"first_name":"Carol","last_name":"White"},"billing_address":{"street_and_nr":"10 Downing Street","city":"London","post_code":"SW1A 2AA","country":"GB"},"shipping_address":{"street_and_nr":"10 Downing Street","city":"London","post_code":"SW1A 2AA","country":"GB"},"items":[{"product_id":"SKU-D400","unit_price":"120.00","quantity":1}],"created_at":1700000000000,"shipped_at":1700100000000}'
)
```

4. Unhappy path — an order where the `shipping_address` is missing `post_code`:

```sql
INSERT INTO orders_json VALUES (
  'ORD-2024-9999',
  '{"order_id":"ORD-2024-9999","order_status":"CREATED","customer":{"first_name":"Dan","last_name":"Brown"},"shipping_address":{"street_and_nr":"1 Main St","city":"Anytown","country":"US"},"items":[{"product_id":"SKU-E500","unit_price":"5.99","quantity":1}],"created_at":1700000000000}'
)
```

Because the function is called with `failOnError` = `FALSE`, no record is emitted for this order.
If you check the statement log, you will see a `WARN` entry reporting the problem.

