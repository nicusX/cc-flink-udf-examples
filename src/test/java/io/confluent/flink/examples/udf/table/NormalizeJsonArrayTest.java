package io.confluent.flink.examples.udf.table;

import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizeJsonArrayTest {

    private final List<Row> collected = new ArrayList<>();
    private final NormalizeJsonArray unnest = new NormalizeJsonArray();

    @BeforeEach
    void setUp() throws Exception {
        // Initialize the UDF
        unnest.open(null);
        // Set the collector to capture the output
        unnest.setCollector(new Collector<>() {
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
    void evalArrayWithSingleEntry() {
        String json = """
                ["only@example.com"]
                """;

        unnest.eval(json, false);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getField(0)).isEqualTo(0);
        assertThat(collected.get(0).getField(1)).isEqualTo("only@example.com");
    }

    @Test
    void evalArrayWithThreeEntries() {
        String json = """
                ["lura.gleason@yahoo.com","latricia.becker@hotmail.com","john.doe@gmail.com"]
                """;

        unnest.eval(json, false);

        assertThat(collected).hasSize(3);
        assertThat(collected.get(0).getField(0)).isEqualTo(0);
        assertThat(collected.get(0).getField(1)).isEqualTo("lura.gleason@yahoo.com");
        assertThat(collected.get(1).getField(0)).isEqualTo(1);
        assertThat(collected.get(1).getField(1)).isEqualTo("latricia.becker@hotmail.com");
        assertThat(collected.get(2).getField(0)).isEqualTo(2);
        assertThat(collected.get(2).getField(1)).isEqualTo("john.doe@gmail.com");
    }

    @Test
    void evalEmptyArrayCollectsNothing() {
        unnest.eval("[]", false);

        assertThat(collected).isEmpty();
    }

    @Test
    void evalNonArrayJsonCollectsNothing() {
        String json = """
                {"email": "test@example.com"}
                """;

        unnest.eval(json, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void evalInvalidJsonFailOnErrorThrows() {
        assertThatThrownBy(() -> unnest.eval("not valid json", true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Exception parsing JSON payload:");
    }
}
