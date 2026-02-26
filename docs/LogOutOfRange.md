## LogOutOfRange

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

UDF source code:
[LogOutOfRange](../src/main/java/io/confluent/flink/examples/udf/scalar/LogOutOfRange.java)

This example demonstrates logging in a UDF.

The function is passthrough and simply returns the first parameter passed. However, it also logs a message at `WARN`
level when certain conditions are met.

### Register the function

```sql
CREATE FUNCTION `log_out_of_range`
    AS 'io.confluent.flink.examples.udf.scalar.LogOutOfRange'
    USING JAR 'confluent-artifact://<artifact-id>'
```

### Test the function, logging prices out of range
```sql
SELECT
    order_id,
    log_out_of_range(price, 20, 90) AS _price
FROM  `examples`.`marketplace`.`orders`
```

To see the logs, go to *Environments* > your environment > *Flink* > *Statements* > select your statement > *Logs*.
You can filter log entries by Source (`Function`) and Log level (`WARN`).
