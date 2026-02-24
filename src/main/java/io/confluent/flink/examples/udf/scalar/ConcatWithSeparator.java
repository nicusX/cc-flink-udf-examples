package io.confluent.flink.examples.udf.scalar;

import org.apache.flink.table.functions.ScalarFunction;

/**
 * Simple scalar function concatenating strings with a separator between them.
 *
 * This example also demonstrates how you can have multiple, overloaded eval() methods.
 * Note that, at the time of writing, Confluent Cloud Flink does not yet support vararg parameters.
 *
 * For a similar function, using optional named parameters see {@link ConcatWithSeparatorOptional}.
 */
public class ConcatWithSeparator extends ScalarFunction {

    public String eval(String a, String b, String separator) {
        return a + separator + b;
    }

    public String eval(String a, String b, String c, String separator) {
        return a + separator + b + separator + c;
    }

    public String eval(String a, String b, String c, String d, String separator) {
        return a + separator + b + separator + c + separator + d;
    }
}
