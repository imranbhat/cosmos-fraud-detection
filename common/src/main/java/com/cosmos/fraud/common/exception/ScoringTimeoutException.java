package com.cosmos.fraud.common.exception;

public class ScoringTimeoutException extends FraudPlatformException {

    private static final String ERROR_CODE = "SCORING_TIMEOUT";

    public ScoringTimeoutException(long elapsedMs) {
        super(ERROR_CODE, "Scoring exceeded 45ms timeout. Elapsed: " + elapsedMs + "ms");
    }

    public ScoringTimeoutException(long elapsedMs, Throwable cause) {
        super(ERROR_CODE, "Scoring exceeded 45ms timeout. Elapsed: " + elapsedMs + "ms", cause);
    }
}
