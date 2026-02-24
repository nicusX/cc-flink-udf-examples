package io.confluent.flink.examples.udf.scalar;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.functions.ScalarFunction;

/**
 * Scalar function concatenating multiple strings with a separator between them.
 * <p>
 * This example demonstrates how to use named, optional parameters.
 * <p>
 * This is an alternative implementation of {@link ConcatWithSeparator}.
 *
 * FIXME this function gives an error when invoked with named parameters
 */
public class ConcatWithSeparatorNamed extends ScalarFunction {

    public String eval(
            @ArgumentHint(name = "s1", type = @DataTypeHint("STRING"), isOptional = false) String s1,
            @ArgumentHint(name = "s2", type = @DataTypeHint("STRING"), isOptional = true) String s2,
            @ArgumentHint(name = "s3", type = @DataTypeHint("STRING"), isOptional = true) String s3,
            @ArgumentHint(name = "s4", type = @DataTypeHint("STRING"), isOptional = true) String s4,
            @ArgumentHint(name = "sep", type = @DataTypeHint("STRING"), isOptional = false) String separator) {
        String result = s1;
        if (s2 != null) {
            result = result + separator + s2;
        }
        if (s3 != null) {
            result = result + separator + s3;
        }
        if (s4 != null) {
            result = result + separator + s4;
        }

        return result;
    }
}
