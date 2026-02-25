# Flink User Defined Function examples

Examples of User Defined Functions for Confluent Cloud Flink.

## Examples

### Scalar Functions

* Simple scalar function, multiple overloaded `eval()`
  methods: [ConcatWithSeparator](./src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparator.java)
* Logging: [LogOutOfRange](./src/main/java/io/confluent/flink/examples/udf/scalar/LogOutOfRange.java)
* Non-deterministic
  function: [RandomString](./src/main/java/io/confluent/flink/examples/udf/scalar/RandomString.java)

### Table Functions

* Unnesting JSON fields: [JsonAddressToRow](./src/main/java/io/confluent/flink/examples/udf/table/JsonAddressToRow.java)

---

## Building and deploying the UDFs

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

### Testing the UDFs in this repository

Follow the additional instructions to test the UDF examples from this repository:

1. [Testing scalar functions](./docs/scalar_functions.md)
2. [Testing table functions](./docs/table_functions.md)

---

## Defining Java Dependencies in a UDF project

Check out the [`pom.xml`](pom.xml) of this project:

* The Java target version is set to 17. Confluent Cloud currently supports Java 11 and 17.
  You can compile the project with a JDK newer than 17, but the build target is set to 17 by the POM.
  If you have a JDK 11 you can change `target.java.version` to `11` to avoid compilation errors.
* At the time of writing, the latest Flink API supported for Confluent Cloud UDFs is `2.1.0`.
* The POM creates an uber-jar which includes all additional dependencies needed by the UDF, using `maven-shade-plugin`.
* Flink dependencies, such as `org.apache.flink:flink-table-api-java` and Log4j have scope `provided` so they are not included
  in the uber-jar. It's important not to include any Flink dependency because it may cause conflict with what's provided
  by the Confluent Cloud runtime.
