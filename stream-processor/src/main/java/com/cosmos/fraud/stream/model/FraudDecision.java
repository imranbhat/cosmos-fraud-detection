package com.cosmos.fraud.stream.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a fraud decision event consumed from the "fraud.decisions" Kafka topic.
 * <p>
 * Published by the scoring-service after evaluating a transaction. The
 * {@link com.cosmos.fraud.stream.job.FraudAlertAggregationJob} aggregates these
 * events in tumbling windows to detect merchant-level decline spikes.
 */
public final class FraudDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique transaction identifier. */
    private String txId;

    /** Merchant associated with the transaction. */
    private String merchantId;

    /**
     * Decision outcome.
     * <p>
     * Typical values: {@code "APPROVE"}, {@code "DECLINE"}, {@code "REVIEW"}.
     */
    private String decision;

    /** Risk score assigned by the scoring-service (0–1000). */
    private int riskScore;

    /** Event time of the decision (epoch millis). */
    private long decisionTimestamp;

    // -----------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------

    /** Required by Avro and Flink serialisation. */
    public FraudDecision() {
    }

    public FraudDecision(
            String txId,
            String merchantId,
            String decision,
            int riskScore,
            long decisionTimestamp) {
        this.txId = Objects.requireNonNull(txId, "txId");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.riskScore = riskScore;
        this.decisionTimestamp = decisionTimestamp;
    }

    // -----------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public long getDecisionTimestamp() { return decisionTimestamp; }
    public void setDecisionTimestamp(long decisionTimestamp) { this.decisionTimestamp = decisionTimestamp; }

    /** Convenience helper used by aggregation logic. */
    public boolean isDecline() {
        return "DECLINE".equalsIgnoreCase(decision);
    }

    @Override
    public String toString() {
        return "FraudDecision{txId='" + txId + "', merchantId='" + merchantId
                + "', decision='" + decision + "', riskScore=" + riskScore
                + ", decisionTimestamp=" + decisionTimestamp + '}';
    }
}
