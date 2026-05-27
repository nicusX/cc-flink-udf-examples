package io.confluent.flink.examples.udf.table;

import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataContractEnforcerTest {

    private final List<Row> collected = new ArrayList<>();
    private final DataContractEnforcer udf = new DataContractEnforcer();

    @BeforeEach
    void setUp() throws Exception {
        udf.open(null);
        udf.setCollector(new Collector<>() {
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
    void validRowPassesThrough() {
        udf.eval("ORD-001", 42, "SKU-A100", 29.99, 0, 0L, true);

        assertThat(collected).hasSize(1);
        Row output = collected.get(0);
        assertThat(output.getField("order_id")).isEqualTo("ORD-001");
        assertThat(output.getField("customer_id")).isEqualTo(42);
        assertThat(output.getField("product_id")).isEqualTo("SKU-A100");
        assertThat(output.getField("price")).isEqualTo(29.99);
    }

    @Test
    void nullOrderIdFailOnErrorThrows() {
        assertThatThrownBy(() -> udf.eval(null, 42, "SKU-A100", 29.99, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order_id");
    }

    @Test
    void nullOrderIdNoFailCollectsNothing() {
        udf.eval(null, 42, "SKU-A100", 29.99, 0, 0L, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void nullCustomerIdFailOnErrorThrows() {
        assertThatThrownBy(() -> udf.eval("ORD-001", null, "SKU-A100", 29.99, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customer_id");
    }

    @Test
    void nullCustomerIdNoFailCollectsNothing() {
        udf.eval("ORD-001", null, "SKU-A100", 29.99, 0, 0L, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void nullProductIdFailOnErrorThrows() {
        assertThatThrownBy(() -> udf.eval("ORD-001", 42, null, 29.99, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product_id");
    }

    @Test
    void nullProductIdNoFailCollectsNothing() {
        udf.eval("ORD-001", 42, null, 29.99, 0, 0L, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void nullPriceFailOnErrorThrows() {
        assertThatThrownBy(() -> udf.eval("ORD-001", 42, "SKU-A100", null, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    void nullPriceNoFailCollectsNothing() {
        udf.eval("ORD-001", 42, "SKU-A100", null, 0, 0L, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void negativePriceFailOnErrorThrows() {
        assertThatThrownBy(() -> udf.eval("ORD-001", 42, "SKU-A100", -5.0, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    void negativePriceNoFailCollectsNothing() {
        udf.eval("ORD-001", 42, "SKU-A100", -5.0, 0, 0L, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void zeroPriceFailOnErrorThrows() {
        assertThatThrownBy(() -> udf.eval("ORD-001", 42, "SKU-A100", 0.0, 0, 0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    void zeroPriceNoFailCollectsNothing() {
        udf.eval("ORD-001", 42, "SKU-A100", 0.0, 0, 0L, false);

        assertThat(collected).isEmpty();
    }

    @Test
    void nullPartitionAndOffsetPassesThrough() {
        udf.eval("ORD-001", 42, "SKU-A100", 29.99, null, null, false);

        assertThat(collected).hasSize(1);
    }
}
