package com.cosmos.fraud.common.kafka;

public final class KafkaTopics {

    public static final String TRANSACTIONS_RAW = "transactions.raw";
    public static final String TRANSACTIONS_ENRICHED = "transactions.enriched";
    public static final String FEATURES_UPDATES = "features.updates";
    public static final String FRAUD_DECISIONS = "fraud.decisions";
    public static final String FRAUD_ALERTS = "fraud.alerts";

    private KafkaTopics() {
    }
}
