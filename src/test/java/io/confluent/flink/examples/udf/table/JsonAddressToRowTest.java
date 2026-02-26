package io.confluent.flink.examples.udf.table;

import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonAddressToRowTest {

    private final List<Row> collected = new ArrayList<>();
    private final JsonAddressToRow jsonToRow = new JsonAddressToRow();

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the UDF
        jsonToRow.open(null);
        // Set the collector to capture the output
        jsonToRow.setCollector(new Collector<>() {
            @Override
            public void collect(Row row) {
                collected.add(row);
            }

            @Override
            public void close() {
            }
        });
        collected.clear();
    }

    @Test
    void evalExtractsAllFields() {
        String json = """
                { "street" : "898 Hill Islands", "postcode": "16143", "city" : "Lake Ernestoburgh"}
                """;

        jsonToRow.eval(json, true);

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);
        assertThat(row.getField(0)).isEqualTo("898 Hill Islands");
        assertThat(row.getField(1)).isEqualTo("16143");
        assertThat(row.getField(2)).isEqualTo("Lake Ernestoburgh");
    }

    @Test
    void evalHandlesMissingField() {
        String json = """
                { "street": "771 Kemmer Flat", "city": "South Abeview" }
                """;

        jsonToRow.eval(json, true);

        assertThat(collected).hasSize(1);
        Row row = collected.get(0);
        assertThat(row.getField(0)).isEqualTo("771 Kemmer Flat");
        assertThat(row.getField(1)).isNull();
        assertThat(row.getField(2)).isEqualTo("South Abeview");
    }

    @Test
    void evalInvalidJsonFailOnErrorThrows() {
        assertThatThrownBy(() -> jsonToRow.eval("not valid json", true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exception parsing JSON payload:");
    }

    @Test
    void evalInvalidJsonNoFailOnErrorCollectsNothing() {
        jsonToRow.eval("not valid json", false);

        assertThat(collected).isEmpty();
    }
}