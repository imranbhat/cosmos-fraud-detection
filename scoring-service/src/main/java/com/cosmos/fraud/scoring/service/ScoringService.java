package com.cosmos.fraud.scoring.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cosmos.fraud.avro.Decision;
import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.avro.FraudDecision;
import com.cosmos.fraud.scoring.config.ScoringProperties;
import com.cosmos.fraud.scoring.engine.RuleEngine;
import com.cosmos.fraud.scoring.engine.RuleResult;
import com.cosmos.fraud.scoring.ml.ModelInferenceService;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    /**
     * Maximum possible score from rule engine (sum of all rule max scores):
     * VelocityRule(30) + AmountAnomalyRule(25) + GeoAnomalyRule(40)
     * + MerchantCategoryRule(15) + DeviceChangeRule(20) + TimeAnomalyRule(10) = 140
     */
    private static final int MAX_RULE_SCORE = 140;
    private static final int MAX_FINAL_SCORE = 1000;
    private static final int MIN_FINAL_SCORE = 0;

    private final RuleEngine ruleEngine;
    private final ModelInferenceService modelInferenceService;
    private final ScoringProperties properties;

    public ScoringService(RuleEngine ruleEngine,
                          ModelInferenceService modelInferenceService,
                          ScoringProperties properties) {
        this.ruleEngine = ruleEngine;
        this.modelInferenceService = modelInferenceService;
        this.properties = properties;
    }

    public FraudDecision score(EnrichedTransaction tx) {
        long startMs = System.currentTimeMillis();

        // 1. Run rule engine
        RuleResult ruleResult = ruleEngine.evaluate(tx);

        // 2. Attempt ML inference
        Optional<Double> mlProbability = modelInferenceService.predict(tx);

        // 3. Compute final score (0–1000)
        int finalScore = computeFinalScore(ruleResult.totalScore(), mlProbability);

        // 4. Determine decision
        Decision decision = determineDecision(finalScore);

        // 5. Build model scores map
        Map<String, Double> modelScores = buildModelScores(ruleResult, mlProbability);

        long latencyMs = System.currentTimeMillis() - startMs;

        log.debug("Scored txId={}: ruleScore={}, mlProb={}, finalScore={}, decision={}, latencyMs={}",
                tx.getTxId(), ruleResult.totalScore(),
                mlProbability.map(p -> String.format("%.4f", p)).orElse("N/A"),
                finalScore, decision, latencyMs);

        return FraudDecision.newBuilder()
                .setTxId(tx.getTxId())
                .setCardId(tx.getCardId())
                .setRiskScore(finalScore)
                .setDecision(decision)
                .setAppliedRules(List.copyOf(ruleResult.triggeredRules()))
                .setModelScores(new java.util.HashMap<>(modelScores))
                .setLatencyMs(latencyMs)
                .setTimestamp(System.currentTimeMillis())
                .build();
    }

    private int computeFinalScore(int ruleScore, Optional<Double> mlProbability) {
        double rawScore;
        if (mlProbability.isPresent()) {
            // Normalise rule score to 0-1000 range before weighting
            double normalisedRuleScore = MAX_RULE_SCORE > 0
                    ? ((double) ruleScore / MAX_RULE_SCORE) * 1000.0
                    : 0.0;
            double mlScore = mlProbability.get() * 1000.0;
            rawScore = (normalisedRuleScore * properties.getRuleWeight())
                    + (mlScore * properties.getMlWeight());
        } else {
            // Rules-only fallback: normalise rule score to 0-1000
            rawScore = MAX_RULE_SCORE > 0
                    ? ((double) ruleScore / MAX_RULE_SCORE) * 1000.0
                    : 0.0;
        }

        // Clamp to [0, 1000]
        return (int) Math.max(MIN_FINAL_SCORE, Math.min(MAX_FINAL_SCORE, Math.round(rawScore)));
    }

    private Decision determineDecision(int finalScore) {
        if (finalScore <= properties.getApproveThreshold()) {
            return Decision.APPROVE;
        } else if (finalScore >= properties.getChallengeThreshold()) {
            return Decision.DECLINE;
        }
        return Decision.CHALLENGE;
    }

    private Map<String, Double> buildModelScores(RuleResult ruleResult, Optional<Double> mlProbability) {
        Map<String, Double> modelScores = new HashMap<>();
        // Include per-rule scores as doubles for auditability
        ruleResult.ruleScores().forEach((rule, score) -> modelScores.put("rule." + rule, (double) score));
        mlProbability.ifPresent(p -> modelScores.put("ml.onnx", p));
        return modelScores;
    }
}
