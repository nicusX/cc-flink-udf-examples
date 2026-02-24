# Flink UDF examples

> *****************************************
> ⚠️ THIS REPOSITORY IS A WORK IN PROGRESS
> *****************************************

Collection of examples of User Defined Functions for Confluent Cloud Flink.



## Examples

### Scalar Functions

* Simple scalar function, multiple overloaded `eval()`
  methods: [ConcatWithSeparator](./src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparator.java)
* Scalar function with named, optional
  parameters: [ConcatWithSeparatorNamed](./src/main/java/io/confluent/flink/examples/udf/scalar/ConcatWithSeparatorNamed.java)
* Logging: [LogOutOfRange](./src/main/java/io/confluent/flink/examples/udf/scalar/LogOutOfRange.java)
* Non-deterministic function; initializing
  resources: [RandomString](./src/main/java/io/confluent/flink/examples/udf/scalar/RandomString.java)
* TODO Flattening (denormalizing) JSON arrays (JSON->ROW)

### Table Functions

TODO

## Building, deploying, and testing UDFs

### Java Dependencies

TODO (Flink API version, provided dependencies)

### Packaging and loading the artifact

TODO (uber-jar, upload in the correct cloud provider and region)

### Registering the UDF

TODO for usage in SQL

### Data

These examples use sample data from the `marketplace` catalog in the `examples` environment available in any Confluent
Cloud Flink organization.
