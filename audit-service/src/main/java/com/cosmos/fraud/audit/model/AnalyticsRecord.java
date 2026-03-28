package com.cosmos.fraud.audit.model;

import java.time.Instant;

/**
 * Flattened analytics record written to ClickHouse for OLAP queries.
 * Uses primitives where possible to minimise boxing overhead in batch inserts.
 */
public record AnalyticsRecord(
        String txId,
        String cardId,
        String merchantId,
        double amount,
        int riskScore,
        String decision,
        long latencyMs,
        Instant timestamp,
        String country,
        String channel,
        String mcc
) {}
