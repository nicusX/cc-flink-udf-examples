## Usage of Scalar UDF examples

> *****************************************
> ⚠️ THIS DOC IS A WORK IN PROGRESS
> *****************************************

Register the scalar UDF provided by the artifact


TODO Select your catalog and database in SQL Workspace


### Registering and Invoking UDFs

#### ConcatWithSeparator

Register:
```sql
CREATE FUNCTION `concat_with_separator`
    AS 'io.confluent.flink.examples.udf.scalar.ConcatWithSeparator'
  USING JAR 'confluent-artifact://<artifact-id>'
```

TODO: verify the function is registered correctly

Invoke:
```sql
SELECT 
  concat_with_separator(`name`, `brand`, ' - ') AS long_name,
  concat_with_separator(`name`, `brand`, `vendor`, ' : ') AS extended_name,
    concat_with_separator(`name`, `brand`, `vendor`, `department`, ' : ') AS extra_long_name
FROM `examples`.`marketplace`.`products`
```

Note: if you try to invoke the UDF with parameters not matching any of the `eval()` implementations, you get an error.
for example, invoking `concat_with_separator(...)` with just two parameters:

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


#### LogOutOfRange

Register:
```sql
CREATE FUNCTION `log_out_of_range`
    AS 'io.confluent.flink.examples.udf.scalar.LogOutOfRange'
    USING JAR 'confluent-artifact://<artifact-id>'
```

Invoke:
```sql
SELECT
    order_id,
    log_out_of_range(price, 20, 90) AS _price
FROM  `examples`.`marketplace`.`orders`   
```

To see the logs, go to *Environments* > your environment > *Flink* > *Statements* > select your statement > *Logs*.

UDF log entries are from the Source `Function`

#### RandomString

Register:
```sql
CREATE FUNCTION `random_string`
    AS 'io.confluent.flink.examples.udf.scalar.RandomString'
    USING JAR 'confluent-artifact://<artifact-id>'
```

Invoke: (⚠️ **FAILS WITH: Your statement encountered an error. This may be due to invalid syntax, configuration, or a transient system issue. Please review your code and access permissions for all required topics and schemas. Once verified, please retry the statement. If the issue persists, contact Confluent support.)
```sql
SELECT
    *,
    random_string(10) AS my_random
FROM  `examples`.`marketplace`.`orders`    
```