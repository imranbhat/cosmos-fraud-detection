package com.cosmos.fraud.scoring.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;

@DisplayName("AmountAnomalyRule")
class AmountAnomalyRuleTest {

    private AmountAnomalyRule rule;

    @BeforeEach
    void setUp() {
        rule = new AmountAnomalyRule();
    }

    @Test
    @DisplayName("returns 0 for normal amount (below 2x average)")
    void normalAmount_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(100.0)
                .avgAmountSevenDays(100.0)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 0 for amount exactly at 2x threshold (boundary, not strictly greater)")
    void amountExactlyTwoX_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(200.0)
                .avgAmountSevenDays(100.0)
                .build();
        // amount > avg * 2.0 is false at exactly 2x → score 0
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 10 for amount slightly above 2x average")
    void amountJustAboveTwoX_returns10() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(200.01)
                .avgAmountSevenDays(100.0)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(10);
    }

    @Test
    @DisplayName("returns 10 for amount between 2x and 3x average")
    void amountBetweenTwoAndThreeX_returns10() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(250.0)
                .avgAmountSevenDays(100.0)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(10);
    }

    @Test
    @DisplayName("returns 25 for amount strictly above 3x average")
    void amountAboveThreeX_returns25() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(300.01)
                .avgAmountSevenDays(100.0)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(25);
    }

    @Test
    @DisplayName("returns 25 for very large amount (10x average)")
    void veryLargeAmount_returns25() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(1000.0)
                .avgAmountSevenDays(100.0)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(25);
    }

    @Test
    @DisplayName("returns 0 when average is zero (no historical data)")
    void zeroAverage_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .amount(500.0)
                .avgAmountSevenDays(0.0)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("rule name is AMOUNT_ANOMALY")
    void ruleName_isAmountAnomaly() {
        assertThat(rule.name()).isEqualTo("AMOUNT_ANOMALY");
    }
}
