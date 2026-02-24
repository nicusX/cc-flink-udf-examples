package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcatWithSeparatorOptionalTest {

    private final ConcatWithSeparatorOptional concatWithDashOptional = new ConcatWithSeparatorOptional();

    @Test
    void evalWithOnlyRequiredArguments() {
        assertThat(concatWithDashOptional.eval("hello", null, null, null, "-"))
                .isEqualTo("hello");
    }

    @Test
    void evalWithTwoStrings() {
        assertThat(concatWithDashOptional.eval("hello", "world", null, null, "-"))
                .isEqualTo("hello-world");
    }

    @Test
    void evalWithThreeStrings() {
        assertThat(concatWithDashOptional.eval("a", "b", "c", null, "-"))
                .isEqualTo("a-b-c");
    }

    @Test
    void evalWithAllFourStrings() {
        assertThat(concatWithDashOptional.eval("a", "b", "c", "d", "-"))
                .isEqualTo("a-b-c-d");
    }

    @Test
    void evalWithCustomSeparator() {
        assertThat(concatWithDashOptional.eval("a", "b", "c", "d", ", "))
                .isEqualTo("a, b, c, d");
    }

    @Test
    void evalWithEmptySeparator() {
        assertThat(concatWithDashOptional.eval("hello", "world", null, null, ""))
                .isEqualTo("helloworld");
    }

    @Test
    void evalSkipsNullInMiddle() {
        assertThat(concatWithDashOptional.eval("a", null, null, "d", "-"))
                .isEqualTo("a-d");
    }
}