## DataContractEnforcer

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

UDF source code:
[DataContractEnforcer](../src/main/java/io/confluent/flink/examples/udf/table/DataContractEnforcer.java)

Example of Table User Defined Function enforcing a specific data contract, such as type, nullability, and value range.

Note that in this simple example the data contract is hardwired in the function code.


### Register the User Defined Function (UDF)

Register the function. Replace `<artifact-id>` with the ID of the JAR artifact you uploaded.
The artifact ID is a string starting with `cfa-` like `cfa-abc1234`.

```sql
CREATE FUNCTION `validate_order`
    AS 'io.confluent.flink.examples.udf.table.DataContractEnforcer'
  USING JAR 'confluent-artifact://<artifact-id>'
```

Verify registration:
```sql
DESCRIBE FUNCTION EXTENDED `validate_order`
```

### Testing the UDTF

#### Create tables

```sql
-- Raw (unvalidated) orders, including partition and offset metadata
CREATE TABLE `orders_raw` (
    `order_id` STRING,
    `customer_id` INT,
    `product_id` STRING,
    `price` DOUBLE,
    `partition` INT METADATA VIRTUAL,
    `offset` BIGINT METADATA VIRTUAL
) WITH (
    'kafka.consumer.isolation-level' = 'read-uncommitted',
    'scan.startup.mode' = 'latest-offset'
)
```

```sql
-- Validated orders
CREATE TABLE `orders_valid` (
    `order_id` STRING NOT NULL,
    `customer_id` INT NOT NULL,
    `product_id` STRING NOT NULL,
    `price` DOUBLE NOT NULL
) WITH (
    'kafka.consumer.isolation-level' = 'read-uncommitted',
    'scan.startup.mode' = 'latest-offset'    
)
```

#### Visualize the output

```sql
SELECT * FROM `orders_valid`;
```

Keep this select statement running.

#### Test the UDTF

Test either with `fail_on_error = false` and `fail_on_error = true`

```sql
-- Option a: Pass valid records only and log errors (fail_on_error = false)
INSERT INTO `orders_valid`(`order_id`, `customer_id`, `product_id`, `price`)
SELECT `v`.`order_id`, `v`.`customer_id`, `v`.`product_id`, `v`.`price`
FROM `orders_raw` AS `r`,
LATERAL TABLE(validate_order(
    order_id => `r`.`order_id`, 
    customer_id => `r`.`customer_id`, 
    product_id => `r`.`product_id`, 
    price => `r`.`price`, 
    `partition` => `r`.`partition`, 
    `offset` => `r`.`offset`, 
    `fail_on_error` => false)) AS `v`
```


```sql
-- Option b: Fail on any invalid record (fail_on_error = true)
INSERT INTO `orders_valid`(`order_id`, `customer_id`, `product_id`, `price`)
SELECT `v`.`order_id`, `v`.`customer_id`, `v`.`product_id`, `v`.`price`
FROM `orders_raw` AS `r`,
LATERAL TABLE(validate_order(
    order_id => `r`.`order_id`, 
    customer_id => `r`.`customer_id`, 
    product_id => `r`.`product_id`, 
    price => `r`.`price`, 
    `partition` => `r`.`partition`, 
    `offset` => `r`.`offset`, 
    `fail_on_error` => true)) AS `v`
```

#### Play test data

Play some test data

```sql
-- 10 valid records
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES
    ('ORD-001', 42, 'SKU-A100', 29.99),
    ('ORD-002', 13, 'SKU-A009', 43.56),
    ('ORD-003', 7, 'SKU-B200', 9.50),
    ('ORD-004', 25, 'SKU-C301', 149.00),
    ('ORD-005', 3, 'SKU-A100', 29.99),
    ('ORD-006', 18, 'SKU-D410', 74.25),
    ('ORD-007', 42, 'SKU-B200', 19.00),
    ('ORD-008', 31, 'SKU-E500', 5.99),
    ('ORD-009', 9, 'SKU-C301', 149.00),
    ('ORD-010', 56, 'SKU-F620', 220.00);

-- Missing order_id
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES (CAST(NULL AS STRING), 42, 'SKU-A100', 29.99);

-- Missing customer_id
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES ('ORD-011', CAST(NULL AS INT), 'SKU-A100', 29.99);

-- Missing product_id
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES ('ORD-012', 42, CAST(NULL AS STRING), 29.99);

-- Missing price
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES ('ORD-013', 42, 'SKU-A100', CAST(NULL AS DOUBLE));

-- Negative price
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES ('ORD-014', 42, 'SKU-A100', -5.0);

-- Zero price
INSERT INTO `orders_raw` (`order_id`, `customer_id`, `product_id`, `price`)
VALUES ('ORD-015', 42, 'SKU-A100', 0.0);
```

