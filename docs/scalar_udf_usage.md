## Usage of Scalar UDF examples

Register the scalar UDF provided by the artifact

### Registering the UDFs 



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
  `product_id`,
  concat_with_separator(`name`, `brand`, ' - ') AS long_name,
  concat_with_separator(`name`, `brand`, `vendor`, ' : ') AS extended_name
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

#### ConcatWithSeparatorOptional

Register:
```sql
CREATE FUNCTION `concat_with_separator_opt`
    AS 'io.confluent.flink.examples.udf.scalar.ConcatWithSeparatorOptional'
  USING JAR 'confluent-artifact://<artifact-id>'
```


Verify the function is registered correctly:
```sql
DESCRIBE FUNCTION `concat_with_separator_opt`

```

Invoke:
```sql
SELECT 
  `product_id`,
  concat_with_separator_opt(`name`, ', ') AS simple_name,
  concat_with_separator_opt(`name`, `brand`, ' - ') AS long_name,
  concat_with_separator_opt(`name`, `brand`, `vendor`, ' : ') AS extended_name,
  concat_with_separator_opt(`name`, `brand`, `vendor`, `department`, ' + ') AS extra_long_name  
FROM `examples`.`marketplace`.`products`
```