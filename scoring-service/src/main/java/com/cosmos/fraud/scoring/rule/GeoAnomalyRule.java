package com.cosmos.fraud.scoring.rule;

import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

@Component
public class GeoAnomalyRule implements Rule {

    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final int HIGH_GEO_SCORE = 40;
    private static final int MEDIUM_GEO_SCORE = 15;
    private static final int NO_RISK_SCORE = 0;

    @Override
    public String name() {
        return "GEO_ANOMALY";
    }

    @Override
    public int evaluate(EnrichedTransaction transaction) {
        boolean countryChanged = transaction.getCountryChanged();
        if (!countryChanged) {
            return NO_RISK_SCORE;
        }

        long timeSinceLastTx = transaction.getTimeSinceLastTxMs();
        if (timeSinceLastTx < ONE_HOUR_MS) {
            return HIGH_GEO_SCORE;
        }
        return MEDIUM_GEO_SCORE;
    }
}
