package com.cosmos.fraud.audit.mapper;

import com.cosmos.fraud.audit.model.AnalyticsRecord;
import com.cosmos.fraud.audit.model.AuditRecord;
import com.cosmos.fraud.avro.FraudDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless mapper between Avro-generated types and domain models.
 * All methods are static — no instantiation needed.
 */
public final class AuditMapper {

    private AuditMapper() {
        // utility class
    }

    /**
     * Converts a {@link FraudDecision} Avro record into an {@link AuditRecord}.
     *
     * <p>Fields not present in the Avro schema (merchantId, amount, country, channel, mcc)
     * are left as empty strings / zero so that callers can enrich them from the raw event
     * if needed.
     *
     * @param decision  the Avro FraudDecision record received from Kafka
     * @param rawEvent  the full JSON string of the originating event for replay
     * @return a populated AuditRecord
     */
    public static AuditRecord fromFraudDecision(FraudDecision decision, String rawEvent) {
        List<String> appliedRules = decision.getAppliedRules() != null
                ? new ArrayList<>(decision.getAppliedRules().stream()
                        .map(CharSequence::toString)
                        .toList())
                : List.of();

        Map<String, Double> modelScores = new HashMap<>();
        if (decision.getModelScores() != null) {
            decision.getModelScores().forEach(
                    (k, v) -> modelScores.put(k.toString(), v));
        }

        return new AuditRecord(
                decision.getTxId().toString(),
                decision.getCardId().toString(),
                /* merchantId — not in FraudDecision schema; enriched downstream */ "",
                /* amount     — not in FraudDecision schema; enriched downstream */ BigDecimal.ZERO,
                decision.getRiskScore(),
                decision.getDecision().name(),
                appliedRules,
                modelScores,
                decision.getLatencyMs(),
                Instant.ofEpochMilli(decision.getTimestamp()),
                rawEvent
        );
    }

    /**
     * Projects an {@link AuditRecord} into the lightweight {@link AnalyticsRecord}
     * suitable for batch-insertion into ClickHouse.
     *
     * @param audit the full audit record
     * @return a flattened analytics record
     */
    public static AnalyticsRecord toAnalyticsRecord(AuditRecord audit) {
        return new AnalyticsRecord(
                audit.txId(),
                audit.cardId(),
                audit.merchantId(),
                audit.amount().doubleValue(),
                audit.riskScore(),
                audit.decision(),
                audit.latencyMs(),
                audit.timestamp(),
                /* country  — populated from raw event at enrichment time; default empty */ "",
                /* channel  — populated from raw event at enrichment time; default empty */ "",
                /* mcc      — populated from raw event at enrichment time; default empty */ ""
        );
    }
}
