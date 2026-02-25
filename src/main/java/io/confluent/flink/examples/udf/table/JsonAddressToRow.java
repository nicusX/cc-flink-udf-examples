package io.confluent.flink.examples.udf.table;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * User Defined Table Function (UDTF) parsing a STRING containing valid JSON, looking for specific fields, and returning
 * a ROW with the fields parsed.
 * <p>
 * In this example the implementation parses a JSON like the following:
 * <code>{ "street" : "91839 Satterfield Wall", "postcode": "05420", "city" : "Wunschtown" }</code>
 * and returns a ROW with street, postcode, and city.
 * <p>
 * Note that the parsing logic can be arbitrarily complex, and also navigate nested elements.
 * <p>
 * The example also shows how to initialize resources expensive to create, like ObjectMapper, once when the function is
 * initialized.
 * <p>
 * The function also implements two different behaviors when an invalid JSON is encountered, depending on the second parameter
 * passed to the function invocation.
 */
@FunctionHint(output = @DataTypeHint("ROW<street STRING, postcode STRING, city STRING>"))
public class JsonAddressToRow extends TableFunction<Row> {
    private static final Logger LOGGER = LogManager.getLogger(JsonAddressToRow.class);

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

            // Extract the three specific fields we are looking for, defaulting to null if the field is not present.
            // Using path() instead of get() because get() returns null for missing fields, causing a NullPointerException.
            String street = node.path("street").asText(null);
            String postcode = node.path("postcode").asText(null);
            String city = node.path("city").asText(null);

            // Collect the output. Always a single Row in this example
            collect(Row.of(street, postcode, city));
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
