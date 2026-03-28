package com.cosmos.fraud.audit.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable audit record persisted to ScyllaDB for every fraud decision.
 */
public record AuditRecord(
        String txId,
        String cardId,
        String merchantId,
        BigDecimal amount,
        int riskScore,
        String decision,
        List<String> appliedRules,
        Map<String, Double> modelScores,
        long latencyMs,
        Instant timestamp,
        /** Full JSON representation of the originating event for replay capability. */
        String rawEvent
) {}
