package com.cosmos.fraud.scoring.engine;

import java.util.List;
import java.util.Map;

public record RuleResult(
        int totalScore,
        Map<String, Integer> ruleScores,
        List<String> triggeredRules
) {
}
