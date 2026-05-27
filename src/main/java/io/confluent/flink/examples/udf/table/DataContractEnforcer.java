package io.confluent.flink.examples.udf.table;

import org.apache.flink.table.annotation.ArgumentHint;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * User Defined Table Function (UDTF) enforcing some data quality rules on the input.
 * Emits a record containing all input fields, if validations are passed.
 * <p>
 * On failure, either throw an exception (stop the statement) or skip the offending record, depending on the
 * `fail_on_error` parameter. In either case, the function log the offending record at WARN level.
 * Partition and offset metadata are passed to the input for logging only.
 * <p>
 * Note that the input parameters are more lenient (e.g. accept null) than the validation rules and the output record.
 */
@FunctionHint(
        output = @DataTypeHint("ROW<`order_id` STRING NOT NULL, `customer_id` INT NOT NULL, `product_id` STRING NOT NULL, `price` DOUBLE NOT NULL>"))
public class DataContractEnforcer extends TableFunction<Row> {
    private static final Logger LOGGER = LogManager.getLogger(DataContractEnforcer.class);


    public void eval(
            @ArgumentHint(name = "order_id", type = @DataTypeHint("STRING")) String orderId,
            @ArgumentHint(name = "customer_id", type = @DataTypeHint("INT")) Integer customerId,
            @ArgumentHint(name = "product_id", type = @DataTypeHint("STRING")) String productId,
            @ArgumentHint(name = "price", type = @DataTypeHint("DOUBLE")) Double price,
            @ArgumentHint(name = "partition", type = @DataTypeHint("INT"), isOptional = true) Integer partition,
            @ArgumentHint(name = "offset", type = @DataTypeHint("BIGINT"), isOptional = true) Long offset,
            @ArgumentHint(name = "fail_on_error", type = @DataTypeHint("BOOLEAN NOT NULL")) boolean failOnError) {
        try {
            // Parse the input with some validation. Throws IllegalArgumentException if any validation rule is violated
            Row outputRow = Row.withNames();
            outputRow.setField("order_id", requireNonNull(orderId, "order_id"));
            outputRow.setField("customer_id", requireNonNull(customerId, "customer_id"));
            outputRow.setField("product_id", requireNonNull(productId, "product_id"));
            outputRow.setField("price", requirePositive(requireNonNull(price, "price"), "price"));
            collect(outputRow);

            // DO NOT log on every record at INFO level in production - this may consume resources and logging can be throttled
            LOGGER.info("Valid message processed at partition:{}, offset:{}", partition, offset);
        } catch (IllegalArgumentException ve) {
            // Always log the problem at WARN level
            LOGGER.warn("Validation error at partition:{}, offset:{}, issue:'{}'", partition, offset, ve.getMessage());

            // Conditionally rethrow the exception, causing the Flink statement to stop
            if (failOnError) {
                throw ve;
            }
        }
    }

    private <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field " + fieldName);
        }
        return value;
    }

    private double requirePositive(double value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException("Field " + fieldName + " must be positive");
        }
        return value;
    }
}
