package com.cosmos.fraud.audit.repository;

import com.cosmos.fraud.audit.model.AuditRecord;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ScyllaDB repository for {@link AuditRecord}.
 *
 * <p>All DML is executed via {@link PreparedStatement}s prepared once at startup
 * to avoid repeated parsing overhead and benefit from ScyllaDB's LWT optimisation.
 *
 * <p>Table DDL (expected to exist):
 * <pre>{@code
 * CREATE TABLE fraud_audit.transactions (
 *     tx_id        text,
 *     card_id      text,
 *     merchant_id  text,
 *     amount       decimal,
 *     risk_score   int,
 *     decision     text,
 *     applied_rules list<text>,
 *     model_scores  map<text, double>,
 *     latency_ms   bigint,
 *     timestamp    timestamp,
 *     raw_event    text,
 *     PRIMARY KEY (tx_id)
 * );
 * CREATE INDEX ON fraud_audit.transactions (card_id);
 * }</pre>
 */
@Repository
public class AuditRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);

    private static final String INSERT_CQL = """
            INSERT INTO transactions (
                tx_id, card_id, merchant_id, amount, risk_score, decision,
                applied_rules, model_scores, latency_ms, timestamp, raw_event
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_TX_ID_CQL = """
            SELECT tx_id, card_id, merchant_id, amount, risk_score, decision,
                   applied_rules, model_scores, latency_ms, timestamp, raw_event
            FROM transactions
            WHERE tx_id = ?
            """;

    private static final String FIND_BY_CARD_ID_CQL = """
            SELECT tx_id, card_id, merchant_id, amount, risk_score, decision,
                   applied_rules, model_scores, latency_ms, timestamp, raw_event
            FROM transactions
            WHERE card_id = ? AND timestamp >= ? AND timestamp <= ?
            ALLOW FILTERING
            """;

    private final CqlSession session;

    private PreparedStatement insertStmt;
    private PreparedStatement findByTxIdStmt;
    private PreparedStatement findByCardIdStmt;

    public AuditRepository(CqlSession session) {
        this.session = session;
    }

    @PostConstruct
    void prepareStatements() {
        insertStmt       = session.prepare(INSERT_CQL);
        findByTxIdStmt   = session.prepare(FIND_BY_TX_ID_CQL);
        findByCardIdStmt = session.prepare(FIND_BY_CARD_ID_CQL);
        log.info("ScyllaDB prepared statements initialised");
    }

    /**
     * Persists an {@link AuditRecord} to the {@code fraud_audit.transactions} table.
     *
     * @param record the audit record to save
     */
    public void save(AuditRecord record) {
        BoundStatement bound = insertStmt.bind(
                record.txId(),
                record.cardId(),
                record.merchantId(),
                record.amount(),
                record.riskScore(),
                record.decision(),
                record.appliedRules(),
                record.modelScores(),
                record.latencyMs(),
                record.timestamp(),
                record.rawEvent()
        );
        session.execute(bound);
        log.debug("Saved audit record txId={}", record.txId());
    }

    /**
     * Looks up a single audit record by transaction ID.
     *
     * @param txId the transaction identifier
     * @return an {@link Optional} containing the record if found
     */
    public Optional<AuditRecord> findByTxId(String txId) {
        BoundStatement bound = findByTxIdStmt.bind(txId);
        ResultSet rs = session.execute(bound);
        Row row = rs.one();
        return Optional.ofNullable(row).map(AuditRepository::toAuditRecord);
    }

    /**
     * Returns all audit records for a card within the specified time range.
     *
     * @param cardId the card identifier
     * @param from   range start (inclusive)
     * @param to     range end (inclusive)
     * @return a list of matching records, possibly empty
     */
    public List<AuditRecord> findByCardId(String cardId, Instant from, Instant to) {
        BoundStatement bound = findByCardIdStmt.bind(cardId, from, to);
        ResultSet rs = session.execute(bound);
        List<AuditRecord> results = new ArrayList<>();
        for (Row row : rs) {
            results.add(toAuditRecord(row));
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static AuditRecord toAuditRecord(Row row) {
        List<String> appliedRules = row.getList("applied_rules", String.class);
        Map<String, Double> modelScores = row.getMap("model_scores", String.class, Double.class);

        return new AuditRecord(
                row.getString("tx_id"),
                row.getString("card_id"),
                row.getString("merchant_id"),
                row.getBigDecimal("amount"),
                row.getInt("risk_score"),
                row.getString("decision"),
                appliedRules != null ? appliedRules : List.of(),
                modelScores  != null ? new HashMap<>(modelScores) : Map.of(),
                row.getLong("latency_ms"),
                row.getInstant("timestamp"),
                row.getString("raw_event")
        );
    }
}
