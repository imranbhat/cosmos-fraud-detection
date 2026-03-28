package com.cosmos.fraud.scoring.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;

@DisplayName("GeoAnomalyRule")
class GeoAnomalyRuleTest {

    private GeoAnomalyRule rule;

    @BeforeEach
    void setUp() {
        rule = new GeoAnomalyRule();
    }

    @Test
    @DisplayName("returns 0 when country has not changed")
    void noCountryChange_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .countryChanged(false)
                .timeSinceLastTxMs(1_800_000L) // 30 minutes
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 15 when country changed but more than 1 hour has passed")
    void countryChangedMoreThanOneHour_returns15() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .countryChanged(true)
                .timeSinceLastTxMs(7_200_000L) // 2 hours
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(15);
    }

    @Test
    @DisplayName("returns 15 when country changed at exactly 1 hour boundary")
    void countryChangedAtExactlyOneHour_returns15() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .countryChanged(true)
                .timeSinceLastTxMs(3_600_000L) // exactly 1 hour — not strictly less
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(15);
    }

    @Test
    @DisplayName("returns 40 when country changed within 1 hour (impossible speed)")
    void countryChangedWithinOneHour_returns40() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .countryChanged(true)
                .timeSinceLastTxMs(1_800_000L) // 30 minutes
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(40);
    }

    @Test
    @DisplayName("returns 40 for very short time since last transaction with country change")
    void countryChangedVeryShortTime_returns40() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .countryChanged(true)
                .timeSinceLastTxMs(60_000L) // 1 minute
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(40);
    }

    @Test
    @DisplayName("rule name is GEO_ANOMALY")
    void ruleName_isGeoAnomaly() {
        assertThat(rule.name()).isEqualTo("GEO_ANOMALY");
    }
}
