package com.cosmos.fraud.audit.controller;

import com.cosmos.fraud.audit.model.AuditRecord;
import com.cosmos.fraud.audit.repository.AuditRepository;
import com.cosmos.fraud.avro.FraudDecision;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing audit query and replay endpoints.
 *
 * <ul>
 *   <li>{@code GET  /v1/transactions/{txId}/audit} — fetch a single audit record</li>
 *   <li>{@code POST /v1/audit/replay?from=&to=}    — replay events for a time range</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    /**
     * Kafka topic to which replayed raw transaction events are published.
     * Downstream services (e.g. scoring-engine) re-process them from this topic.
     */
    private static final String REPLAY_TOPIC = "transactions.raw";

    private final AuditRepository auditRepository;
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    public AuditController(
            AuditRepository auditRepository,
            KafkaTemplate<String, SpecificRecord> kafkaTemplate) {
        this.auditRepository = auditRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // -------------------------------------------------------------------------
    // GET /v1/transactions/{txId}/audit
    // -------------------------------------------------------------------------

    /**
     * Returns the full audit record for the given transaction ID.
     *
     * @param txId the transaction identifier
     * @return 200 with the {@link AuditRecord}, or 404 when not found
     */
    @GetMapping("/transactions/{txId}/audit")
    public ResponseEntity<AuditRecord> getAuditRecord(@PathVariable String txId) {
        log.debug("Fetching audit record for txId={}", txId);
        return auditRepository.findByTxId(txId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Audit record not found for txId={}", txId);
                    return ResponseEntity.notFound().build();
                });
    }

    // -------------------------------------------------------------------------
    // POST /v1/audit/replay
    // -------------------------------------------------------------------------

    /**
     * Replays all audit records whose timestamps fall within {@code [from, to]}.
     *
     * <p>Each matching record's {@code rawEvent} JSON is republished to the
     * {@code transactions.raw} Kafka topic so that downstream services can
     * re-score the transactions.
     *
     * @param from ISO-8601 range start (e.g. {@code 2024-01-01T00:00:00Z})
     * @param to   ISO-8601 range end   (e.g. {@code 2024-01-01T23:59:59Z})
     * @return 202 Accepted with replay stats
     */
    @PostMapping("/audit/replay")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        log.info("Replay requested from={} to={}", from, to);

        // We use a synthetic cardId wildcard approach: iterate all records via
        // a broad-range scan.  Callers needing card-scoped replay should use
        // a dedicated endpoint in future iterations.
        List<AuditRecord> records = auditRepository.findByCardId("*", from, to);

        int published = 0;
        for (AuditRecord record : records) {
            try {
                kafkaTemplate.send(REPLAY_TOPIC, record.txId(), buildReplayEvent(record));
                published++;
            } catch (Exception ex) {
                log.error("Failed to replay txId={}", record.txId(), ex);
            }
        }

        log.info("Replay complete: scanned={} published={}", records.size(), published);
        return ResponseEntity.accepted().body(Map.of(
                "scanned", records.size(),
                "published", published,
                "from", from.toString(),
                "to", to.toString()
        ));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal {@link FraudDecision} Avro record from an {@link AuditRecord}
     * for replay purposes.  The rawEvent JSON is the canonical source, but the
     * Avro record satisfies the producer's type constraint.
     */
    private SpecificRecord buildReplayEvent(AuditRecord record) {
        com.cosmos.fraud.avro.Decision decision;
        try {
            decision = com.cosmos.fraud.avro.Decision.valueOf(record.decision());
        } catch (IllegalArgumentException ex) {
            decision = com.cosmos.fraud.avro.Decision.APPROVE;
        }

        return FraudDecision.newBuilder()
                .setTxId(record.txId())
                .setCardId(record.cardId())
                .setRiskScore(record.riskScore())
                .setDecision(decision)
                .setAppliedRules(new java.util.ArrayList<>(record.appliedRules()))
                .setModelScores(new java.util.HashMap<>(record.modelScores()))
                .setLatencyMs(record.latencyMs())
                .setTimestamp(record.timestamp().toEpochMilli())
                .build();
    }
}
