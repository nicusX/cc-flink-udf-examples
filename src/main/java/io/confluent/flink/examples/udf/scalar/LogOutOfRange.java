package io.confluent.flink.examples.udf.scalar;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.functions.ScalarFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Scalar passthrough (returns the argument as-is) function logging a value when outside a specified range.
 * <p>
 * Note that logging from UDF is throttled.
 * See: <a href="https://docs.confluent.io/cloud/current/flink/how-to-guides/enable-udf-logging.html#limitations">Confluent Cloud UDF docs</a>
 */
public class LogOutOfRange extends ScalarFunction {
    private static final Logger LOGGER = LogManager.getLogger(LogOutOfRange.class);

    public Double eval(double value, double lowerBound, double upperBound) {

        if (value < lowerBound) {
            LOGGER.info("Value {} below lower bound {}", value, lowerBound);
        }
        if (value > upperBound) {
            LOGGER.info("Value {} above upper bound {}", value, upperBound);
        }

        return value; // pass-through
    }
}
