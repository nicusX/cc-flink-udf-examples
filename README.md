# Flink User Defined Function examples

This repository contains examples of User Defined Functions for Confluent Cloud Flink in Java.

The examples also illustrate a realistic deployment workflow for a Flink SQL statement using a user defined function.
Details are in the [User Defined Functions Deployment Lifecycle](./lifecycle.md) page.

## Java User Defined Function examples

### Scalar Functions (UDF)

* Simple scalar function:
  [ConcatWithSeparator](./src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparator.java), [usage](./docs/ConcatWithSeparator.md) -
  Also shows multiple overloaded `eval()` implementations.
* Logging from UDF: [LogOutOfRange](./src/main/java/io/confluent/flink/examples/udf/scalar/LogOutOfRange.java), [usage](./docs/LogOutOfRange.md).
* Non-deterministic
  function: [RandomString](./src/main/java/io/confluent/flink/examples/udf/scalar/RandomString.java), [usage](./docs/RandomString.md).

### Table Functions (UDTF)

* Extract JSON fields:
  [JsonAddressToRow](./src/main/java/io/confluent/flink/examples/udf/table/JsonAddressToRow.java), [usage](./docs/JsonAddressToRow.md) -
  Extract specific fields from a string field containing JSON.
* Normalize JSON nested elements:
  [NormalizeJsonArray](./src/main/java/io/confluent/flink/examples/udf/table/NormalizeJsonArray.java), [usage](./docs/NormalizeJsonArray.md) -
  Extract nested elements from a string containing JSON, emitting one row per element.
* Parse a structured JSON payload:
  [ParseStructuredJson](./src/main/java/io/confluent/flink/examples/udf/table/ParseStructuredJson.java), [usage](./docs/ParseStructuredJson.md) -
  Parse a complex, structured JSON payload without a schema, execute programmatic validation, and emit a single ROW
  containing nested SQL elements such as ARRAYs and ROWs.

### Process Table Functions (PTF)

> ℹ️Process Table Functions (PTF) are currently not yet publicly available in Confluent Cloud Flink.

* Entity State Machine: 
  [EntityStateMachine](src/main/java/io/confluent/flink/examples/ptf/statemachine/EntityStateMachine.java), [usage](docs/EntityStateMachine-PTF.md).

---

## Building and deploying the user defined functions

This repository provides a [POM file](pom.xml) with all required dependencies and configurations to build these examples.

For an explanation about handling dependencies in your UDF projects, see [Java dependencies in UDFs](./java-dependencies.md).

### Packaging the artifact

All user defined functions of this repo can be built using Maven and are packaged in a single JAR file.

Build the artifact:
```shell
mvn package
```

### Loading the artifact

To use the UDFs, you need to upload the artifact (the JAR file) first.

Go to *Environments* > select your environment > *Artifacts*.

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

Before using a UDF in SQL, you need to register it using a `CREATE FUNCTION` statement:

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

### Testing the functions in SQL

Follow the additional instructions to test the UDF examples you find in this repository:

1. [ConcatWithSeparator](./docs/ConcatWithSeparator.md)
2. [LogOutOfRange](./docs/LogOutOfRange.md)
3. [RandomString](./docs/RandomString.md)
4. [JsonAddressToRow](./docs/JsonAddressToRow.md)
5. [NormalizeJsonArray](./docs/NormalizeJsonArray.md)
6. [ParseStructuredJson](./docs/ParseStructuredJson.md)
7. [EntityStateMachine (PTF)](./docs/EntityStateMachine-PTF.md)

