package io.confluent.flink.examples.udf.scalar;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test is for demonstration purposes.
 * A single RandomString instance is used for all test, and it is initialized
 * calling the open() method. This emulates what happens in Flink.
 */
class RandomStringTest {

    private static final RandomString randomString = new RandomString();

    @BeforeAll
    static void setUp() throws Exception {
        randomString.open(null);
    }

    @Test
    void evalReturnsStringOfRequestedLength() {
        assertThat(randomString.eval(10)).hasSize(10);
    }

    @Test
    void evalReturnsAlphanumericString() {
        assertThat(randomString.eval(20)).matches("[a-zA-Z0-9]+");
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