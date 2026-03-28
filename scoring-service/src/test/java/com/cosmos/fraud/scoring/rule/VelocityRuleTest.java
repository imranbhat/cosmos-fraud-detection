package com.cosmos.fraud.scoring.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;

@DisplayName("VelocityRule")
class VelocityRuleTest {

    private VelocityRule rule;

    @BeforeEach
    void setUp() {
        rule = new VelocityRule();
    }

    @Test
    @DisplayName("returns 0 for low velocity (txCountOneHour <= 5)")
    void lowVelocity_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder().txCountOneHour(3).build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 0 for exactly 5 transactions (boundary)")
    void exactlyFiveTransactions_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder().txCountOneHour(5).build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 15 for medium velocity (txCountOneHour > 5 and <= 10)")
    void mediumVelocity_returns15() {
        EnrichedTransaction tx = TestDataFactory.builder().txCountOneHour(7).build();
        assertThat(rule.evaluate(tx)).isEqualTo(15);
    }

    @Test
    @DisplayName("returns 15 for exactly 10 transactions (boundary)")
    void exactlyTenTransactions_returns15() {
        EnrichedTransaction tx = TestDataFactory.builder().txCountOneHour(10).build();
        assertThat(rule.evaluate(tx)).isEqualTo(15);
    }

    @Test
    @DisplayName("returns 30 for high velocity (txCountOneHour > 10)")
    void highVelocity_returns30() {
        EnrichedTransaction tx = TestDataFactory.builder().txCountOneHour(15).build();
        assertThat(rule.evaluate(tx)).isEqualTo(30);
    }

    @Test
    @DisplayName("returns 30 for exactly 11 transactions (boundary)")
    void elevenTransactions_returns30() {
        EnrichedTransaction tx = TestDataFactory.builder().txCountOneHour(11).build();
        assertThat(rule.evaluate(tx)).isEqualTo(30);
    }

    @Test
    @DisplayName("rule name is VELOCITY")
    void ruleName_isVelocity() {
        assertThat(rule.name()).isEqualTo("VELOCITY");
    }
}
