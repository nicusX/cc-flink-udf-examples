package io.confluent.flink.examples.udf.table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;

/**
 * User Defined Table Function (UDTF) parsing a STRING containing a structured JSON order into a
 * single ROW with nested ROW and ARRAY types.
 * <p>
 * This example demonstrates how to map a complex nested JSON structure — containing objects,
 * arrays, and nullable fields — into Flink's SQL type system using nested ROW types and ARRAY.
 * <p>
 * The function expects a JSON order payload and emits a single row with: order metadata, a nested
 * customer object, optional billing/shipping addresses, an array of line items, and timestamp
 * fields.
 */
@FunctionHint(output = @DataTypeHint("ROW<" +
        "order_id STRING NOT NULL, " +
        "order_status STRING NOT NULL, " +
        "customer ROW<first_name STRING NOT NULL, middle_name STRING, last_name STRING NOT NULL> NOT NULL, " +
        "billing_address ROW<street_and_nr STRING NOT NULL, city STRING NOT NULL, post_code STRING NOT NULL, country STRING NOT NULL>, " +
        "shipping_address ROW<street_and_nr STRING NOT NULL, city STRING NOT NULL, post_code STRING NOT NULL, country STRING NOT NULL>, " +
        "items ARRAY<ROW<product_id STRING NOT NULL, unit_price DECIMAL(9,2) NOT NULL, quantity INT NOT NULL>> NOT NULL, " +
        "created_at BIGINT NOT NULL, " +
        "shipped_at BIGINT, " +
        "delivered_at BIGINT" +
        ">"))
public class ParseStructuredJson extends TableFunction<Row> {
    private static final Logger LOGGER = LogManager.getLogger(ParseStructuredJson.class);

    // IMPORTANT: any reused resource must be at instance level (not static) and must be initialized in open(), not at the definition.
    // It must also be marked as `transient`. Failing to do this causes a serialization error.
    private transient ObjectMapper mapper;

    @Override
    public void open(FunctionContext context) throws Exception {
        try {
            mapper = new ObjectMapper();
        } catch (Exception ex) {
            LOGGER.error("UDF open() failed", ex);
            throw ex;
        }
    }

    public void eval(String json, boolean failOnError) {
        // DEBUG logging is not captured when running the UDTF in Confluent Cloud Flink
        LOGGER.debug("Parsing JSON string: {}", json);

        try {
            JsonNode node = mapper.readTree(json);

            /// Navigate the JSON structure extracting the fields.
            /// Also validates fields, like mandatory (NOT NULL) and throw an exception if validation is violated.

            // Top level fields
            String orderId = requireString(node, "order_id");
            String orderStatus = requireString(node, "order_status");
            long createdAt = requireLong(node, "created_at");
            Long shippedAt = optionalLong(node, "shipped_at");
            Long deliveredAt = optionalLong(node, "delivered_at");

            // Parse nested customer object (mandatory, with mandatory sub-fields)
            Row customerRow = parseCustomer(node.path("customer"));

            // Parse optional address objects (nullable, but fields are mandatory when present)
            Row billingRow = optionalAddress(node.path("billing_address"));
            Row shippingRow = optionalAddress(node.path("shipping_address"));

            // Parse items array (mandatory, with mandatory fields per item)
            JsonNode itemsNode = node.path("items");
            Row[] itemsArray = new Row[itemsNode.size()];
            for (int i = 0; i < itemsNode.size(); i++) {
                itemsArray[i] = parseItem(itemsNode.get(i));
            }


            // Emit the record, if no error has happened
            collect(Row.of(orderId, orderStatus, customerRow, billingRow, shippingRow,
                    itemsArray, createdAt, shippedAt, deliveredAt));
        } catch (RuntimeException | JsonProcessingException e) {
            // This exception is thrown only if the JSON is invalid and unparseable.
            if (failOnError) {
                throw new RuntimeException("Exception parsing JSON payload: " + e.getMessage(), e);
            } else {
                LOGGER.warn("Exception parsing JSON payload: {} - {}", json, e.getMessage());
            }
        }
    }

    /**
     * Parse a mandatory String field. Throw a ValidationException if missing
     */
    private String requireString(JsonNode parent, String fieldName) {
        String value = StringUtils.trimToNull(parent.path(fieldName).asText(null));
        if (value == null) {
            throw new ValidationException("Missing required field: " + fieldName);
        }
        return value;
    }

    /**
     * Parse an optional String field. Return null if missing or empty.
     */
    private String optionalString(JsonNode parent, String fieldName) {
        return StringUtils.trimToNull(parent.path(fieldName).asText(null));
    }

    /**
     * Parse a mandatory Long field. Throw a ValidationException if missing
     */
    private long requireLong(JsonNode parent, String fieldName) {
        JsonNode fieldNode = parent.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            throw new ValidationException("Missing required field: " + fieldName);
        }
        return fieldNode.asLong();
    }

    /**
     * Parse an optional Long. Return null if missing
     */
    private Long optionalLong(JsonNode parent, String fieldName) {
        JsonNode fieldNode = parent.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asLong();
    }

    /**
     * Parse the customer object. Throws ValidationException if missing or null.
     */
    private Row parseCustomer(JsonNode customerNode) {
        if (customerNode.isMissingNode() || customerNode.isNull()) {
            throw new ValidationException("Missing required field: customer");
        }
        return Row.of(
                requireString(customerNode, "first_name"),
                optionalString(customerNode, "middle_name"),
                requireString(customerNode, "last_name")
        );
    }

    /**
     * Parse the address object. Return null if the field is empty
     */
    private Row optionalAddress(JsonNode addressNode) {
        if (addressNode.isMissingNode() || addressNode.isNull()) {
            return null;
        }
        return Row.of(
                requireString(addressNode, "street_and_nr"),
                requireString(addressNode, "city"),
                requireString(addressNode, "post_code"),
                requireString(addressNode, "country")
        );
    }

    /**
     * Parse an item
     */
    private Row parseItem(JsonNode itemNode) {
        return Row.of(
                requireString(itemNode, "product_id"),
                new BigDecimal(itemNode.path("unit_price").asText("0")),
                itemNode.path("quantity").asInt(0)
        );
    }
}
