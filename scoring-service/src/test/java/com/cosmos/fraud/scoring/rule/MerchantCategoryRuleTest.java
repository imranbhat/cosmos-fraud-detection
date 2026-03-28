package com.cosmos.fraud.scoring.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;

@DisplayName("MerchantCategoryRule")
class MerchantCategoryRuleTest {

    private MerchantCategoryRule rule;

    @BeforeEach
    void setUp() {
        rule = new MerchantCategoryRule();
    }

    @ParameterizedTest(name = "returns 0 for normal MCC: {0}")
    @ValueSource(strings = {"5411", "5812", "4111", "7011", "5999"})
    @DisplayName("returns 0 for standard merchant categories")
    void normalMcc_returnsZero(String mcc) {
        EnrichedTransaction tx = TestDataFactory.builder().mcc(mcc).build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @ParameterizedTest(name = "returns 15 for high-risk MCC: {0}")
    @ValueSource(strings = {"7995", "5967", "5912", "5122"})
    @DisplayName("returns 15 for all high-risk merchant categories")
    void highRiskMcc_returns15(String mcc) {
        EnrichedTransaction tx = TestDataFactory.builder().mcc(mcc).build();
        assertThat(rule.evaluate(tx)).isEqualTo(15);
    }

    @ParameterizedTest(name = "rule name is MERCHANT_CATEGORY")
    @ValueSource(strings = {"7995"})
    @DisplayName("rule name is MERCHANT_CATEGORY")
    void ruleName_isMerchantCategory(String mcc) {
        assertThat(rule.name()).isEqualTo("MERCHANT_CATEGORY");
    }
}
