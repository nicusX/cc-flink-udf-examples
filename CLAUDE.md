# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```shell
mvn package                          # Build uber-jar (target/udf-examples-1.0.jar)
mvn test                             # Run all tests
mvn test -Dtest=ConcatWithSeparatorTest  # Run a single test class
```

The uber-jar is created by maven-shade-plugin and bundles all non-provided dependencies. Flink and Log4j dependencies have `provided` scope — they must never be included in the uber-jar as they conflict with the Confluent Cloud runtime.

## Architecture

This is a collection of example UDFs (User Defined Functions) for Confluent Cloud Flink, organized by function type:

- **Scalar functions** (`udf/scalar/`) — extend `ScalarFunction`, implement one or more overloaded `eval()` methods that return a value directly
- **Table functions** (`udf/table/`) — extend `TableFunction<Row>`, implement `eval()` methods that call `collect(Row.of(...))` to emit rows, and require a `@FunctionHint(output = @DataTypeHint("ROW<...>"))` annotation

## Key Conventions

**Resource initialization:** Expensive resources (e.g. ObjectMapper) must be initialized in the `open()` method, not in `eval()` or at field declaration. The field must be marked `transient` to avoid serialization errors. Resources do not need to be thread-safe — Flink creates separate instances per thread.

**Logging:** Use Log4j 2 (`LogManager.getLogger()`). Always use parameterized messages (`LOGGER.info("value: {}", val)`) not string concatenation. DEBUG is not captured in Confluent Cloud — use WARN or INFO.

## Instructional Purpose

This is example code for instructional purposes. Readability and clarity take priority. Each example demonstrates a specific pattern or capability — check class-level Javadoc for what each example teaches.
