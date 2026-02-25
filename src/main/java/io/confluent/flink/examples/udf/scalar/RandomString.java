package io.confluent.flink.examples.udf.scalar;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.table.functions.ScalarFunction;

/**
 * This simple UDF demonstrate the implementation of a function returning a non-deterministic value (i.e. a value that
 * does not depend only on the input parameters).
 *
 * Overriding {@link #isDeterministic()} to return false is important to ensure Flink invoke the function code on every
 * processed record.
 * If Flink thinks a UDF is deterministic (the default) and the function is invoked passing constant parameters (or no parameters)
 * Flink may run it once and reuse the same returned value every time.
 */
public class RandomString extends ScalarFunction {

    @Override
    public boolean isDeterministic() {
        return false;
    }

    public String eval(int length) {
        return RandomStringUtils.secure().nextAlphabetic(length);
    }
}
