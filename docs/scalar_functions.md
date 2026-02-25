## Usage of Scalar UDF examples

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.


### ConcatWithSeparator

UDF source code:
[ConcatWithSeparator](../src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparator.java)

Simple scalar function concatenating two or more string parameters with a specified separator.
This example shows how you can overload the `eval()` method having different versions with different parameters.

#### Register the User Defined Function (UDF)

Register the function. Replace `<artifact-id>` with the ID of the JAR artifact you uploaded.
The artifact ID is a string starting with `cfa-` like `cfa-abc1234`.

```sql
CREATE FUNCTION `concat_with_separator`
    AS 'io.confluent.flink.examples.udf.scalar.ConcatWithSeparator'
  USING JAR 'confluent-artifact://<artifact-id>'
```

Verify registration:
```sql
DESCRIBE FUNCTION EXTENDED `concat_with_separator`
```

#### Test the UDF to concatenate multiple string fields

```sql
SELECT 
  concat_with_separator(`name`, `brand`, ' - ') AS long_name,
  concat_with_separator(`name`, `brand`, `vendor`, ' : ') AS extended_name,
    concat_with_separator(`name`, `brand`, `vendor`, `department`, ' : ') AS extra_long_name
FROM `examples`.`marketplace`.`products`
```

Note: if you try to invoke the UDF with parameters not matching any of the `eval()` implementations, you get an error.
for example:

```sql
SELECT 
  concat_with_separator(`name`, ', ') AS simple_name
FROM `examples`.`marketplace`.`products`
```

Causes an error similar to the following
```
SQL validation failed. Error from line 2, column 3 to line 2, column 37.

Caused by: Function 'concat_with_separator(<CHARACTER>, <CHARACTER>)' does not exist or you do not have permission to access it.
Using current catalog 'my-catalog' and current database 'my-database'.
Supported signatures are:
concat_with_separator(STRING, STRING, STRING)
concat_with_separator(STRING, STRING, STRING, STRING, STRING)
concat_with_separator(STRING, STRING, STRING, STRING)
```

`DESCRIBE FUNCTION <function-name>` lists all the supported signatures

---

### LogOutOfRange

UDF source code:
[LogOutOfRange](../src/main/java/io/confluent/flink/examples/udf/scalar/LogOutOfRange.java)

This example demonstrates logging in a UDF.

The function is passthrough and simply returns the first parameter passed. However, it also logs a message at `WARN` 
level when certain conditions are met.

#### Register the function

```sql
CREATE FUNCTION `log_out_of_range`
    AS 'io.confluent.flink.examples.udf.scalar.LogOutOfRange'
    USING JAR 'confluent-artifact://<artifact-id>'
```

#### Test the function, logging prices out of range
```sql
SELECT
    order_id,
    log_out_of_range(price, 20, 90) AS _price
FROM  `examples`.`marketplace`.`orders`   
```

To see the logs, go to *Environments* > your environment > *Flink* > *Statements* > select your statement > *Logs*.
You can filter log entries by Source (`Function`) and Log level (`WARN`).

---

### RandomString

UDF source code:
[RandomString](../src/main/java/io/confluent/flink/examples/udf/scalar/RandomString.java)

This example shows how to implement a non-deterministic UDF, a function which return value does not only depend deterministically
on the input parameters. If not implemented correctly, when a non-deterministic function is invoked with only constant parameters (or no parameter)
Flink may "optimize" and run the function only once, in the pre-flight phase, and reuse the same value for all invocations.
This is normally not what you want to achieve when you, for example, want to generate a random value.

#### Register the function

```sql
CREATE FUNCTION `random_string`
    AS 'io.confluent.flink.examples.udf.scalar.RandomString'
    USING JAR 'confluent-artifact://<artifact-id>'
```

#### Test the function

```sql
SELECT
    random_string(10) AS my_random,
    order_id
FROM  `examples`.`marketplace`.`orders`    
```