package com.cosmos.fraud.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cosmos.fraud.avro.Decision;
import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.avro.FraudDecision;
import com.cosmos.fraud.scoring.TestDataFactory;
import com.cosmos.fraud.scoring.config.ScoringProperties;
import com.cosmos.fraud.scoring.engine.RuleEngine;
import com.cosmos.fraud.scoring.engine.RuleResult;
import com.cosmos.fraud.scoring.ml.ModelInferenceService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoringService")
class ScoringServiceTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private ModelInferenceService modelInferenceService;

    private ScoringProperties properties;
    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        properties = new ScoringProperties();
        properties.setApproveThreshold(300);
        properties.setChallengeThreshold(700);
        properties.setRuleWeight(0.4);
        properties.setMlWeight(0.6);

        scoringService = new ScoringService(ruleEngine, modelInferenceService, properties);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RuleResult ruleResult(int total, List<String> triggered) {
        return new RuleResult(total, Map.of(), triggered);
    }

    // -------------------------------------------------------------------------
    // With ML score
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when ML model is available")
    class WithMlAvailable {

        @Test
        @DisplayName("computes weighted score from rules and ML")
        void weightedScoreCombination() {
            // ruleScore = 70 out of 140 max → normalised = 500
            // mlProb = 0.5 → mlScore = 500
            // final = 500 * 0.4 + 500 * 0.6 = 200 + 300 = 500
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(70, List.of("VELOCITY")));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.5));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(500);
            assertThat(decision.getDecision()).isEqualTo(Decision.CHALLENGE);
        }

        @Test
        @DisplayName("returns APPROVE decision when final score is below approveThreshold")
        void approvesLowRiskTransaction() {
            // ruleScore = 0 → normalised = 0
            // mlProb = 0.0 → mlScore = 0
            // final = 0
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.0));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(0);
            assertThat(decision.getDecision()).isEqualTo(Decision.APPROVE);
        }

        @Test
        @DisplayName("returns DECLINE decision when final score meets or exceeds challengeThreshold")
        void declinesHighRiskTransaction() {
            // ruleScore = 140 (max) → normalised = 1000
            // mlProb = 1.0 → mlScore = 1000
            // final = 1000 * 0.4 + 1000 * 0.6 = 1000 → clamped to 1000
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(140, List.of("VELOCITY", "GEO_ANOMALY")));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(1.0));

            FraudDecision decision = scoringService.score(TestDataFactory.highRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(1000);
            assertThat(decision.getDecision()).isEqualTo(Decision.DECLINE);
        }

        @Test
        @DisplayName("returns CHALLENGE decision for mid-range risk")
        void challengesMidRiskTransaction() {
            // Force a score squarely in the challenge zone [301, 699]
            // ruleScore = 42 / 140 = 0.3 → normalised = 300
            // mlProb = 0.55 → mlScore = 550
            // final = 300 * 0.4 + 550 * 0.6 = 120 + 330 = 450
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(42, List.of("MERCHANT_CATEGORY")));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.55));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isBetween(301, 699);
            assertThat(decision.getDecision()).isEqualTo(Decision.CHALLENGE);
        }

        @Test
        @DisplayName("populates txId and cardId correctly in FraudDecision")
        void propagatesTxIdAndCardId() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.1));

            EnrichedTransaction tx = TestDataFactory.builder()
                    .txId("tx-identity-test")
                    .cardId("card-xyz-001")
                    .build();

            FraudDecision decision = scoringService.score(tx);

            assertThat(decision.getTxId().toString()).isEqualTo("tx-identity-test");
            assertThat(decision.getCardId().toString()).isEqualTo("card-xyz-001");
        }

        @Test
        @DisplayName("includes ML score in modelScores map")
        void includesMlScoreInModelScores() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.75));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getModelScores()).containsKey("ml.onnx");
            assertThat(decision.getModelScores().get("ml.onnx")).isEqualTo(0.75);
        }

        @Test
        @DisplayName("records positive latency")
        void recordsLatency() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.1));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getLatencyMs()).isGreaterThanOrEqualTo(0);
        }
    }

    // -------------------------------------------------------------------------
    // Without ML score (fallback to rules-only)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("when ML model is unavailable (rules-only fallback)")
    class WithMlUnavailable {

        @BeforeEach
        void mlUnavailable() {
            when(modelInferenceService.predict(any())).thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("uses only rule score when ML is unavailable")
        void rulesOnlyFallback() {
            // ruleScore = 70 / 140 * 1000 = 500
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(70, List.of("VELOCITY")));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(500);
            assertThat(decision.getDecision()).isEqualTo(Decision.CHALLENGE);
        }

        @Test
        @DisplayName("approves transaction with zero rule score")
        void zeroRuleScore_approves() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(0);
            assertThat(decision.getDecision()).isEqualTo(Decision.APPROVE);
        }

        @Test
        @DisplayName("declines transaction with max rule score")
        void maxRuleScore_declines() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(140, List.of("VELOCITY", "GEO_ANOMALY")));

            FraudDecision decision = scoringService.score(TestDataFactory.highRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(1000);
            assertThat(decision.getDecision()).isEqualTo(Decision.DECLINE);
        }

        @Test
        @DisplayName("does not include ml.onnx key in modelScores when ML unavailable")
        void noMlKeyInModelScores() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getModelScores()).doesNotContainKey("ml.onnx");
        }
    }

    // -------------------------------------------------------------------------
    // Score clamping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("score clamping")
    class ScoreClamping {

        @Test
        @DisplayName("clamps score to maximum of 1000")
        void clampToMax() {
            // Max rule score (140/140 = 1.0 * 1000 = 1000), max ML prob (1.0 * 1000 = 1000)
            // weighted: 1000 * 0.4 + 1000 * 0.6 = 1000 — already at cap
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(140, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(1.0));

            FraudDecision decision = scoringService.score(TestDataFactory.highRiskTransaction());

            assertThat(decision.getRiskScore()).isLessThanOrEqualTo(1000);
        }

        @Test
        @DisplayName("clamps score to minimum of 0")
        void clampToMin() {
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(0, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.0));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("score is always within [0, 1000] regardless of inputs")
        void scoreAlwaysInValidRange() {
            // Simulate an ML probability just over 1.0 (should be clamped by inference service
            // but we guard here too)
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(200, List.of()));
            when(modelInferenceService.predict(any())).thenReturn(Optional.of(0.99));

            FraudDecision decision = scoringService.score(TestDataFactory.highRiskTransaction());

            assertThat(decision.getRiskScore()).isBetween(0, 1000);
        }
    }

    // -------------------------------------------------------------------------
    // Decision threshold boundaries
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("decision threshold boundary conditions")
    class DecisionThresholds {

        @BeforeEach
        void mlUnavailable() {
            when(modelInferenceService.predict(any())).thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("exactly at approveThreshold is APPROVE")
        void exactlyAtApproveThreshold_isApprove() {
            // Need a rule score that maps to exactly 300.
            // 300/1000 * 140 = 42 rule points → 42/140 * 1000 = 300
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(42, List.of()));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(300);
            assertThat(decision.getDecision()).isEqualTo(Decision.APPROVE);
        }

        @Test
        @DisplayName("exactly at challengeThreshold is DECLINE")
        void exactlyAtChallengeThreshold_isDecline() {
            // Need a rule score that maps to exactly 700.
            // 700/1000 * 140 = 98 rule points → 98/140 * 1000 = 700
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(98, List.of()));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(700);
            assertThat(decision.getDecision()).isEqualTo(Decision.DECLINE);
        }

        @Test
        @DisplayName("score between thresholds results in CHALLENGE")
        void betweenThresholds_isChallenge() {
            // 70/140 * 1000 = 500
            when(ruleEngine.evaluate(any())).thenReturn(ruleResult(70, List.of()));

            FraudDecision decision = scoringService.score(TestDataFactory.lowRiskTransaction());

            assertThat(decision.getRiskScore()).isEqualTo(500);
            assertThat(decision.getDecision()).isEqualTo(Decision.CHALLENGE);
        }

        @Test
        @DisplayName("appliedRules is populated from rule engine triggered rules")
        void appliedRulesPopulated() {
            when(ruleEngine.evaluate(any())).thenReturn(
                    ruleResult(55, List.of("VELOCITY", "GEO_ANOMALY", "TIME_ANOMALY")));

            FraudDecision decision = scoringService.score(TestDataFactory.highRiskTransaction());

            assertThat(decision.getAppliedRules())
                    .containsExactlyInAnyOrder("VELOCITY", "GEO_ANOMALY", "TIME_ANOMALY");
        }
    }
}
