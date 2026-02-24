package io.confluent.flink.examples.udf.scalar;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.ScalarFunction;

/**
 * Function returning a random string.
 * This example also demonstrate the implementation of a non-deterministic UDF and the usage of external dependencies
 * (Apache Common Lang in this case), and how to initialize expensive resources in the open() method.
 */
public class RandomString extends ScalarFunction {

    /**
     * IMPORTANT: the field must be `transient`.
     * Otherwise, you get a serialization error when the statement invoking this function is initialized.
     */
    private transient RandomStringUtils secureGenerator;

    /**
     * Tells the Flink framework that the result of this UDF is not deterministic,
     * forcing Flink to execute the function on each invocation.
     * <p>
     * If this method is not overridden (the default returns false) and you pass a constant as parameter to
     * the function, Flink Table Planner may decide to invoke the function only once, during the pre-flight, instead
     * of executing the function in the cluster, on every invocation, during record processing.
     */
    @Override
    public boolean isDeterministic() {
        return false;
    }

    /**
     * Initializes "expensive" resources once, before starting the execution.
     */
    @Override
    public void open(FunctionContext context) throws Exception {
        secureGenerator = RandomStringUtils.secure();
    }

    public String eval(int length) {
        return secureGenerator.nextAlphanumeric(length);
    }
}
