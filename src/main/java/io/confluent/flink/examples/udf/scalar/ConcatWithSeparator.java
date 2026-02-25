package io.confluent.flink.examples.udf.scalar;

import org.apache.flink.table.functions.ScalarFunction;

import org.apache.commons.lang3.StringUtils;

/**
 * Simple scalar function concatenating strings with a separator between them.
 * <p>
 * This example demonstrates how you can have multiple, overloaded eval() methods.
 * <p>
 * Note that, at the time of writing, Confluent Cloud Flink does not yet support vararg parameters.
 */
public class ConcatWithSeparator extends ScalarFunction {

    public String eval(String a, String b, String separator) {
        return StringUtils.join(new String[]{a, b}, separator);
    }

    public String eval(String a, String b, String c, String separator) {
        return StringUtils.join(new String[]{a, b, c}, separator);
    }

    public String eval(String a, String b, String c, String d, String separator) {
        return StringUtils.join(new String[]{a, b, c, d}, separator);
    }
}
