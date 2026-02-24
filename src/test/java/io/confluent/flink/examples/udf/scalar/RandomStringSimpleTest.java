package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomStringSimpleTest {

    private final RandomStringSimple randomStringSimple = new RandomStringSimple();

    @Test
    void evalReturnsStringOfRequestedLength() {
        assertThat(randomStringSimple.eval(10)).hasSize(10);
    }

    @Test
    void evalReturnsAlphabeticString() {
        assertThat(randomStringSimple.eval(20)).matches("[a-zA-Z]+");
    }

    @Test
    void evalReturnsDifferentStringsOnSubsequentCalls() {
        String first = randomStringSimple.eval(20);
        String second = randomStringSimple.eval(20);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void evalReturnsEmptyStringForZeroLength() {
        assertThat(randomStringSimple.eval(0)).isEmpty();
    }

    @Test
    void isNotDeterministic() {
        assertThat(randomStringSimple.isDeterministic()).isFalse();
    }
}