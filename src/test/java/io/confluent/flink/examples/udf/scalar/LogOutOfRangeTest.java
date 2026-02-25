package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogOutOfRangeTest {

    private final LogOutOfRange logOutOfRange = new LogOutOfRange();

    @Test
    void evalReturnsValueAsIs() {
        assertThat(logOutOfRange.eval(50, 20, 90)).isEqualTo(50.0);
    }
}
