package com.cosmos.fraud.scoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scoring")
public class ScoringProperties {

    /** Transactions with a risk score below this threshold are approved. */
    private int approveThreshold = 300;

    /** Transactions with a risk score above this threshold are declined. */
    private int challengeThreshold = 700;

    /** Weight applied to rule-based score when combining with ML score. */
    private double ruleWeight = 0.4;

    /** Weight applied to ML model score when combining with rule score. */
    private double mlWeight = 0.6;

    public int getApproveThreshold() {
        return approveThreshold;
    }

    public void setApproveThreshold(int approveThreshold) {
        this.approveThreshold = approveThreshold;
    }

    public int getChallengeThreshold() {
        return challengeThreshold;
    }

    public void setChallengeThreshold(int challengeThreshold) {
        this.challengeThreshold = challengeThreshold;
    }

    public double getRuleWeight() {
        return ruleWeight;
    }

    public void setRuleWeight(double ruleWeight) {
        this.ruleWeight = ruleWeight;
    }

    public double getMlWeight() {
        return mlWeight;
    }

    public void setMlWeight(double mlWeight) {
        this.mlWeight = mlWeight;
    }
}
