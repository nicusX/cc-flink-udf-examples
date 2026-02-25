package io.confluent.flink.examples.udf.scalar;

import org.apache.flink.table.functions.ScalarFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Scalar passthrough (returns the argument as-is) function logging a value when outside a specified range.
 * <p>
 * Note that logging from UDF is throttled.
 * See: <a href="https://docs.confluent.io/cloud/current/flink/how-to-guides/enable-udf-logging.html#limitations">Confluent Cloud UDF docs</a>
 * <p>
 * The idea shown by this function is to conditionally log only when certain conditions are met, instead of logging
 * on every single message. This can help with debugging a UDF, greatly reducing the number of log entries to examine
 * and also reducing the chances of entries being lost because of the log throttling.
 */
public class LogOutOfRange extends ScalarFunction {
    private static final Logger LOGGER = LogManager.getLogger(LogOutOfRange.class);

    public Double eval(double value, double lowerBound, double upperBound) {

        if (value < lowerBound) {
            LOGGER.warn("Value {} below lower bound {}", value, lowerBound);
        }
        if (value > upperBound) {
            LOGGER.warn("Value {} above upper bound {}", value, upperBound);
        }

        return value; // pass-through
    }
}
