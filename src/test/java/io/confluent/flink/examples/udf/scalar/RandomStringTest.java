package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RandomStringTest {

    private final RandomString randomString = new RandomString();

    @Test
    void evalReturnsStringOfRequestedLength() {
        assertThat(randomString.eval(10)).hasSize(10);
    }

    @Test
    void evalReturnsAlphabeticString() {
        assertThat(randomString.eval(20)).matches("[a-zA-Z]+");
    }

    @Test
    void evalReturnsDifferentStringsOnSubsequentCalls() {
        String first = randomString.eval(20);
        String second = randomString.eval(20);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void evalReturnsEmptyStringForZeroLength() {
        assertThat(randomString.eval(0)).isEmpty();
    }

    @Test
    void isNotDeterministic() {
        assertThat(randomString.isDeterministic()).isFalse();
    }
}