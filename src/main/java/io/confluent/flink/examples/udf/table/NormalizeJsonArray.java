package io.confluent.flink.examples.udf.table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * User Defined Table Function (UDTF) parsing a STRING containing a valid JSON array, and extracting one record for each
 * element.
 * <p>
 * This is not supposed to be a replacement of the Flink system function UNNEST, which roughly does the same.
 * <p>
 * The goal is to demonstrate how you can implement an arbitrary logic to normalize a nested JSON.
 * The implementation can be easily expanded with a more complex logic, for example looking for specific elements or fields to extract.
 * <p>
 * The function also implements two different behaviors when an invalid JSON is encountered, depending on the second parameter
 * passed to the function invocation.
 * <p>
 * NOTE: Flink runtime includes a shaded version of Jackson2 at the package
 * org.apache.flink.shaded.jackson2.com.fasterxml.jackson.
 * We could have used it for this example. For demonstration purposes, we use the version of Jackson2 added to the
 * project as an additional dependency.
 */
@FunctionHint(output = @DataTypeHint("ROW<email_index INT, email STRING>"))
public class NormalizeJsonArray extends TableFunction<Row> {
    private static final Logger LOGGER = LogManager.getLogger(NormalizeJsonArray.class);

    // IMPORTANT: any reused resource must be at instance level (not static) and must be initialized in open(), not at the definition.
    // It must also be marked as `transient`. Failing to do this causes a serialization error.
    private transient ObjectMapper mapper;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        // Any expensive resources should be initialized in the open() method and not in eval(), not to be executed on
        // every processed record.
        // These re-used resources do not have to be thread-safe. Flink creates different instances of the function class for
        // each processing thread.
        mapper = new ObjectMapper();
    }


    public void eval(String json, boolean failOnError) {
        // DEBUG logging is not captured when running the UDTF in Confluent Cloud Flink
        LOGGER.debug("Parsing JSON string: {}", json);
        try {
            JsonNode node = mapper.readTree(json);

            // If the node is an array, emit one Row per element
            if (node.isArray()) {
                int index = 0;
                for (JsonNode element : node) {
                    collect(Row.of(index, element.asText()));
                    index++;
                }
            }

        } catch (JsonProcessingException e) {
            // This exception is thrown only if the JSON is invalid and unparseable.

            // Simple conditional error handling
            if (failOnError) {
                throw new RuntimeException("Exception parsing JSON payload: " + e.getMessage(), e);
            } else {
                LOGGER.warn("Exception parsing JSON payload: {} - {}", json, e.getMessage());
            }
        }
    }
}
