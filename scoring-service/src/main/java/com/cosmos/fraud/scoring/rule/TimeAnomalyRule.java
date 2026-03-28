package com.cosmos.fraud.scoring.rule;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

@Component
public class TimeAnomalyRule implements Rule {

    private static final int SUSPICIOUS_HOUR_START = 1;
    private static final int SUSPICIOUS_HOUR_END = 5;
    private static final int TIME_ANOMALY_SCORE = 10;
    private static final int NO_RISK_SCORE = 0;

    @Override
    public String name() {
        return "TIME_ANOMALY";
    }

    @Override
    public int evaluate(EnrichedTransaction transaction) {
        long timestampMs = transaction.getTimestamp();
        int hour = Instant.ofEpochMilli(timestampMs)
                .atZone(ZoneOffset.UTC)
                .getHour();

        if (hour >= SUSPICIOUS_HOUR_START && hour <= SUSPICIOUS_HOUR_END) {
            return TIME_ANOMALY_SCORE;
        }
        return NO_RISK_SCORE;
    }
}
