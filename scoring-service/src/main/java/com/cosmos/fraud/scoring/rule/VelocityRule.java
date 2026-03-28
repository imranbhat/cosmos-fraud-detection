package com.cosmos.fraud.scoring.rule;

import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

@Component
public class VelocityRule implements Rule {

    private static final int HIGH_VELOCITY_THRESHOLD = 10;
    private static final int MEDIUM_VELOCITY_THRESHOLD = 5;
    private static final int HIGH_VELOCITY_SCORE = 30;
    private static final int MEDIUM_VELOCITY_SCORE = 15;
    private static final int NO_RISK_SCORE = 0;

    @Override
    public String name() {
        return "VELOCITY";
    }

    @Override
    public int evaluate(EnrichedTransaction transaction) {
        int txCount = transaction.getTxCountOneHour();
        if (txCount > HIGH_VELOCITY_THRESHOLD) {
            return HIGH_VELOCITY_SCORE;
        } else if (txCount > MEDIUM_VELOCITY_THRESHOLD) {
            return MEDIUM_VELOCITY_SCORE;
        }
        return NO_RISK_SCORE;
    }
}
