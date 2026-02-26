## ConcatWithSeparator

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

UDF source code:
[ConcatWithSeparator](../src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparator.java)

Simple scalar function concatenating two or more string parameters with a specified separator.
This example shows how you can overload the `eval()` method having different versions with different parameters.

### Register the User Defined Function (UDF)

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

### Test the UDF to concatenate multiple string fields

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
