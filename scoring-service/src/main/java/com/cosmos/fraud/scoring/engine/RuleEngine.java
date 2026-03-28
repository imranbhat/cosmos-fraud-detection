package com.cosmos.fraud.scoring.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.rule.Rule;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
        log.info("RuleEngine initialized with {} rules: {}", rules.size(),
                rules.stream().map(Rule::name).toList());
    }

    public RuleResult evaluate(EnrichedTransaction tx) {
        Map<String, Integer> ruleScores = new HashMap<>();
        List<String> triggeredRules = new ArrayList<>();
        int totalScore = 0;

        for (Rule rule : rules) {
            try {
                int score = rule.evaluate(tx);
                ruleScores.put(rule.name(), score);
                if (score > 0) {
                    triggeredRules.add(rule.name());
                    totalScore += score;
                }
            } catch (Exception e) {
                log.error("Rule {} evaluation failed for txId={}: {}",
                        rule.name(), tx.getTxId(), e.getMessage(), e);
                // Continue with remaining rules on failure
            }
        }

        log.debug("Rule evaluation complete: txId={}, totalScore={}, triggeredRules={}",
                tx.getTxId(), totalScore, triggeredRules);

        return new RuleResult(totalScore, Map.copyOf(ruleScores), List.copyOf(triggeredRules));
    }
}
