## RandomString

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

UDF source code:
[RandomString](../src/main/java/io/confluent/flink/examples/udf/scalar/RandomString.java)

This example shows how to implement a non-deterministic UDF, a function which return value does not only depend deterministically
on the input parameters. If not implemented correctly, when a non-deterministic function is invoked with only constant parameters (or no parameter)
Flink may "optimize" and run the function only once, in the pre-flight phase, and reuse the same value for all invocations.
This is normally not what you want to achieve when you, for example, want to generate a random value.

### Register the function

```sql
CREATE FUNCTION `random_string`
    AS 'io.confluent.flink.examples.udf.scalar.RandomString'
    USING JAR 'confluent-artifact://<artifact-id>'
```

### Test the function

```sql
SELECT
    random_string(10) AS my_random,
    order_id
FROM  `examples`.`marketplace`.`orders`
```
