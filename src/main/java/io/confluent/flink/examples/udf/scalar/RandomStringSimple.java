package io.confluent.flink.examples.udf.scalar;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.table.functions.ScalarFunction;

public class RandomStringSimple extends ScalarFunction {

    @Override
    public boolean isDeterministic() {
        return false;
    }

    public String eval(int length) {
        return RandomStringUtils.randomAlphabetic(length);
    }
}
