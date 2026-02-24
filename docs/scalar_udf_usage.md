## Usage of Scalar UDF examples

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

#### ConcatWithSeparatorNamed

FIXME: Calling by name does not work as documented

Register:
```sql
CREATE FUNCTION `concat_with_separator_named`
    AS 'io.confluent.flink.examples.udf.scalar.ConcatWithSeparatorNamed'
  USING JAR 'confluent-artifact://<artifact-id>'
```


Verify the function is registered correctly:
```sql
DESCRIBE FUNCTION `concat_with_separator_named`

```

Invoke with named parameters (⚠️ **FAILS WITH: Unsupported function signature. Function must not be overloaded or use varargs.**):
```sql
SELECT
    concat_with_separator_named( s1 => `name`, sep => ', ') AS simple_name,
    concat_with_separator_named( s1 => `name`, s2 => `brand`, sep => ' - ') AS long_name,
    concat_with_separator_named( s1 => `name`, s2 => `brand`, s3 => `vendor`, sep => ' : ') AS extended_name,
    concat_with_separator_named( s1 => `name`, s2 => `brand`, s3 => `vendor`, s4 => `department`, sep => ' : ') AS extra_long_name
FROM `examples`.`marketplace`.`products`
```
Invoke with positional parameters:
```sql
SELECT
    concat_with_separator_named( `name`, `brand`, `vendor`, `department`, ' : ') AS extra_long_name
FROM `examples`.`marketplace`.`products`
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

Invoke:
```sql
SELECT
    *,
    random_string(10) AS random
FROM  `examples`.`marketplace`.`orders`    
```