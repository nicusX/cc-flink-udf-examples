# Flink User Defined Function examples

Examples of User Defined Functions for Confluent Cloud Flink.

## Examples 

### Scalar Functions

* Simple scalar function, with multiple overloaded `eval()` implementations
  methods: [ConcatWithSeparator](./src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparator.java)
* Logging from UDF: [LogOutOfRange](./src/main/java/io/confluent/flink/examples/udf/scalar/LogOutOfRange.java)
* Non-deterministic
  function: [RandomString](./src/main/java/io/confluent/flink/examples/udf/scalar/RandomString.java)

SQL code to [test the scalar function examples](./docs/scalar_functions.md).


### Table Functions

* Extracting JSON fields - extracting specific fields from a string field containing JSON: 
  [JsonAddressToRow](./src/main/java/io/confluent/flink/examples/udf/table/JsonAddressToRow.java)
* Normalizing JSON nested elements - extracting nested elements from a string containing JSON, emitting one row per element: 
  [NormalizeJsonArray](./src/main/java/io/confluent/flink/examples/udf/table/NormalizeJsonArray.java)

SQL code to [test the table function examples](./docs/table_functions.md).

---

## Building and deploying the UDFs

This repository provides a [POM file](pom.xml) with all required dependencies and configurations to build these examples.

For and explanations about handling dependencies in your UDF projects, see [Java dependencies in UDFs](docs/java_dependencies.md).

### Packaging the artifact

All user defined functions of this repo can be built using maven and are packaged in a single JAR file.

Build the artifact:
```shell
mvn package
```

### Loading the artifact

To use the UDFs you need to upload the artifact (the JAR file) first.

Go to *Environments* > select your environment > *Artifacts*

Upload the artifact selecting *Java* as type of UDF, and being careful to select the same cloud provider and region
where your Compute Pool and Cluster have been created.

> ⚠️ An uploaded artifact is immutable. If you modify the UDFs, you need to delete the old artifact first, or rename
> the new JAR file before uploading the new version.
> You will also have to drop and re-register the UDFs, pointing to the new artifact-id.

---

## Testing the UDFs

### Test data

These examples use sample data from the `marketplace` catalog in the `examples` environment available in any Confluent
Cloud Flink organization.

### Registering UDFs

Before using a UDF in SQL you need to register it using a `CREATE FUNCTION` statement:

```sql
CREATE FUNCTION `<function-name>`
    AS '<implementation-class-FQN>'
  USING JAR 'confluent-artifact://<artifact-id>'
```

For example:

```sql
CREATE FUNCTION `concat_with_separator`
    AS 'io.confluent.flink.examples.udf.scalar.ConcatWithSeparator'
  USING JAR 'confluent-artifact://cfa-abcd123'
```

You can verify the function registration:

```sql
DESCRIBE FUNCTION EXTENDED `<function-name>`
```


> ⚠️ When you load a new artifact, unregister the UDF using `DROP FUNCTION <function-name>` and re-register it.

### Testing these UDF examples

Follow the additional instructions to test the UDF examples you find in this repository:

1. [Testing scalar functions](./docs/scalar_functions.md)
2. [Testing table functions](./docs/table_functions.md)

