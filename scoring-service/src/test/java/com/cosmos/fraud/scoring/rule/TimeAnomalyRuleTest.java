package com.cosmos.fraud.scoring.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;

@DisplayName("TimeAnomalyRule")
class TimeAnomalyRuleTest {

    private TimeAnomalyRule rule;

    @BeforeEach
    void setUp() {
        rule = new TimeAnomalyRule();
    }

    @ParameterizedTest(name = "returns 0 for normal hour: {0}:00 UTC")
    @ValueSource(ints = {0, 6, 9, 10, 12, 15, 18, 22, 23})
    @DisplayName("returns 0 for transactions during normal business hours")
    void normalHour_returnsZero(int hour) {
        EnrichedTransaction tx = TestDataFactory.builder().timestampHour(hour).build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @ParameterizedTest(name = "returns 10 for suspicious hour: {0}:00 UTC")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("returns 10 for transactions between 1 AM and 5 AM UTC")
    void suspiciousHour_returns10(int hour) {
        EnrichedTransaction tx = TestDataFactory.builder().timestampHour(hour).build();
        assertThat(rule.evaluate(tx)).isEqualTo(10);
    }

    @Test
    @DisplayName("returns 10 for 2 AM specifically")
    void twoAm_returns10() {
        EnrichedTransaction tx = TestDataFactory.builder().timestampHour(2).build();
        assertThat(rule.evaluate(tx)).isEqualTo(10);
    }

    @Test
    @DisplayName("returns 0 for midnight (0:00 UTC) — just outside suspicious window")
    void midnight_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder().timestampHour(0).build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 0 for 6 AM UTC — just outside suspicious window")
    void sixAm_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder().timestampHour(6).build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("rule name is TIME_ANOMALY")
    void ruleName_isTimeAnomaly() {
        assertThat(rule.name()).isEqualTo("TIME_ANOMALY");
    }
}
