package com.cosmos.fraud.scoring.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;
import com.cosmos.fraud.scoring.rule.Rule;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleEngine")
class RuleEngineTest {

    @Mock
    private Rule ruleA;

    @Mock
    private Rule ruleB;

    @Mock
    private Rule ruleC;

    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        when(ruleA.name()).thenReturn("RULE_A");
        when(ruleB.name()).thenReturn("RULE_B");
        when(ruleC.name()).thenReturn("RULE_C");
        ruleEngine = new RuleEngine(List.of(ruleA, ruleB, ruleC));
    }

    @Test
    @DisplayName("aggregates scores from all rules")
    void aggregatesScores() {
        when(ruleA.evaluate(any())).thenReturn(10);
        when(ruleB.evaluate(any())).thenReturn(20);
        when(ruleC.evaluate(any())).thenReturn(30);

        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = ruleEngine.evaluate(tx);

        assertThat(result.totalScore()).isEqualTo(60);
    }

    @Test
    @DisplayName("only includes rules with score > 0 in triggeredRules list")
    void triggeredRulesOnlyIncludesNonZeroScores() {
        when(ruleA.evaluate(any())).thenReturn(15);
        when(ruleB.evaluate(any())).thenReturn(0);
        when(ruleC.evaluate(any())).thenReturn(25);

        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = ruleEngine.evaluate(tx);

        assertThat(result.triggeredRules()).containsExactlyInAnyOrder("RULE_A", "RULE_C");
        assertThat(result.triggeredRules()).doesNotContain("RULE_B");
    }

    @Test
    @DisplayName("ruleScores map contains all rules including zero-score ones")
    void ruleScoresMapContainsAllRules() {
        when(ruleA.evaluate(any())).thenReturn(15);
        when(ruleB.evaluate(any())).thenReturn(0);
        when(ruleC.evaluate(any())).thenReturn(25);

        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = ruleEngine.evaluate(tx);

        assertThat(result.ruleScores()).containsEntry("RULE_A", 15);
        assertThat(result.ruleScores()).containsEntry("RULE_B", 0);
        assertThat(result.ruleScores()).containsEntry("RULE_C", 25);
    }

    @Test
    @DisplayName("returns zero total score when no rules fire")
    void noRulesFire_zeroTotalScore() {
        when(ruleA.evaluate(any())).thenReturn(0);
        when(ruleB.evaluate(any())).thenReturn(0);
        when(ruleC.evaluate(any())).thenReturn(0);

        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = ruleEngine.evaluate(tx);

        assertThat(result.totalScore()).isEqualTo(0);
        assertThat(result.triggeredRules()).isEmpty();
    }

    @Test
    @DisplayName("continues evaluation when one rule throws an exception")
    void continuesOnRuleException() {
        when(ruleA.evaluate(any())).thenThrow(new RuntimeException("Simulated rule failure"));
        when(ruleB.evaluate(any())).thenReturn(20);
        when(ruleC.evaluate(any())).thenReturn(10);

        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = ruleEngine.evaluate(tx);

        // RULE_A failed but the others should still contribute
        assertThat(result.totalScore()).isEqualTo(30);
        assertThat(result.triggeredRules()).containsExactlyInAnyOrder("RULE_B", "RULE_C");
    }

    @Test
    @DisplayName("handles empty rule list gracefully")
    void emptyRuleList_returnsZeroScore() {
        RuleEngine emptyEngine = new RuleEngine(List.of());
        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = emptyEngine.evaluate(tx);

        assertThat(result.totalScore()).isEqualTo(0);
        assertThat(result.triggeredRules()).isEmpty();
        assertThat(result.ruleScores()).isEmpty();
    }

    @Test
    @DisplayName("triggered rules list is immutable")
    void triggeredRulesListIsImmutable() {
        when(ruleA.evaluate(any())).thenReturn(10);
        when(ruleB.evaluate(any())).thenReturn(0);
        when(ruleC.evaluate(any())).thenReturn(0);

        EnrichedTransaction tx = TestDataFactory.lowRiskTransaction();
        RuleResult result = ruleEngine.evaluate(tx);

        assertThat(result.triggeredRules()).isUnmodifiable();
    }
}
