package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcatWithSeparatorTest {

    private final ConcatWithSeparator concatWithDash = new ConcatWithSeparator();

    @Test
    void evalConcatenatesTwoStringsWithSeparator() {
        assertThat(concatWithDash.eval("hello", "world", "-")).isEqualTo("hello-world");
    }

    @Test
    void evalConcatenatesThreeStringsWithSeparator() {
        assertThat(concatWithDash.eval("a", "b", "c", "-")).isEqualTo("a-b-c");
    }

    @Test
    void evalWithEmptySeparator() {
        assertThat(concatWithDash.eval("hello", "world", "")).isEqualTo("helloworld");
    }

    @Test
    void evalWithEmptyStrings() {
        assertThat(concatWithDash.eval("", "", "-")).isEqualTo("-");
    }

    @Test
    void evalThreeWithEmptyStrings() {
        assertThat(concatWithDash.eval("", "", "", "-")).isEqualTo("--");
    }

    @Test
    void evalConcatenatesFourStringsWithSeparator() {
        assertThat(concatWithDash.eval("a", "b", "c", "d", "-")).isEqualTo("a-b-c-d");
    }

    @Test
    void evalWithNullInputTreatsAsEmptyString() {
        assertThat(concatWithDash.eval(null, "world", "-")).isEqualTo("-world");
    }

    @Test
    void evalWithNullSeparatorTreatsAsEmptyString() {
        assertThat(concatWithDash.eval("a", "b", null)).isEqualTo("ab");
    }
}