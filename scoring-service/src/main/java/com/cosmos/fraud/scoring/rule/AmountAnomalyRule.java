package com.cosmos.fraud.scoring.rule;

import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

@Component
public class AmountAnomalyRule implements Rule {

    private static final double HIGH_MULTIPLIER = 3.0;
    private static final double MEDIUM_MULTIPLIER = 2.0;
    private static final int HIGH_ANOMALY_SCORE = 25;
    private static final int MEDIUM_ANOMALY_SCORE = 10;
    private static final int NO_RISK_SCORE = 0;

    @Override
    public String name() {
        return "AMOUNT_ANOMALY";
    }

    @Override
    public int evaluate(EnrichedTransaction transaction) {
        double amount = transaction.getAmount();
        double avg = transaction.getAvgAmountSevenDays();

        // Guard against division issues when avg is zero
        if (avg <= 0.0) {
            return NO_RISK_SCORE;
        }

        if (amount > avg * HIGH_MULTIPLIER) {
            return HIGH_ANOMALY_SCORE;
        } else if (amount > avg * MEDIUM_MULTIPLIER) {
            return MEDIUM_ANOMALY_SCORE;
        }
        return NO_RISK_SCORE;
    }
}
