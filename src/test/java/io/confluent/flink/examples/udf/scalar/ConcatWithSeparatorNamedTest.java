package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcatWithSeparatorNamedTest {

    private final ConcatWithSeparatorNamed concatWithSeparatorNamed = new ConcatWithSeparatorNamed();

    @Test
    void evalWithOnlyRequiredArguments() {
        assertThat(concatWithSeparatorNamed.eval("hello", null, null, null, "-"))
                .isEqualTo("hello");
    }

    @Test
    void evalWithTwoStrings() {
        assertThat(concatWithSeparatorNamed.eval("hello", "world", null, null, "-"))
                .isEqualTo("hello-world");
    }

    @Test
    void evalWithThreeStrings() {
        assertThat(concatWithSeparatorNamed.eval("a", "b", "c", null, "-"))
                .isEqualTo("a-b-c");
    }

    @Test
    void evalWithAllFourStrings() {
        assertThat(concatWithSeparatorNamed.eval("a", "b", "c", "d", "-"))
                .isEqualTo("a-b-c-d");
    }

    @Test
    void evalWithCustomSeparator() {
        assertThat(concatWithSeparatorNamed.eval("a", "b", "c", "d", ", "))
                .isEqualTo("a, b, c, d");
    }

    @Test
    void evalWithEmptySeparator() {
        assertThat(concatWithSeparatorNamed.eval("hello", "world", null, null, ""))
                .isEqualTo("helloworld");
    }

    @Test
    void evalSkipsNullInMiddle() {
        assertThat(concatWithSeparatorNamed.eval("a", null, null, "d", "-"))
                .isEqualTo("a-d");
    }
}