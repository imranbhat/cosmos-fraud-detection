package com.cosmos.fraud.audit.repository;

import com.cosmos.fraud.audit.model.AnalyticsRecord;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ClickHouse repository for {@link AnalyticsRecord}.
 *
 * <p>Records are accumulated in an in-memory buffer and flushed to ClickHouse
 * either when the buffer reaches {@code analytics.batch-size} entries or when
 * the {@code analytics.flush-interval-ms} scheduler fires — whichever happens
 * first.  A {@link ReentrantLock} guards concurrent access between the Kafka
 * consumer thread and the scheduler thread.
 *
 * <p>Target table DDL (ClickHouse):
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS transactions_analytics (
 *     tx_id       String,
 *     card_id     String,
 *     merchant_id String,
 *     amount      Float64,
 *     risk_score  Int32,
 *     decision    String,
 *     latency_ms  Int64,
 *     timestamp   DateTime64(3, 'UTC'),
 *     country     String,
 *     channel     String,
 *     mcc         String
 * ) ENGINE = MergeTree()
 * ORDER BY (timestamp, card_id);
 * }</pre>
 */
@Repository
public class AnalyticsRepository {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRepository.class);

    private static final String INSERT_SQL = """
            INSERT INTO transactions_analytics
                (tx_id, card_id, merchant_id, amount, risk_score, decision,
                 latency_ms, timestamp, country, channel, mcc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final int batchSize;

    /** Guarded by {@code lock}. */
    private final List<AnalyticsRecord> buffer = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public AnalyticsRepository(
            JdbcTemplate clickHouseJdbcTemplate,
            @Value("${analytics.batch-size:1000}") int batchSize) {
        this.jdbc = clickHouseJdbcTemplate;
        this.batchSize = batchSize;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Adds a single record to the buffer; flushes immediately if the buffer is full.
     *
     * @param record the analytics record to buffer
     */
    public void add(AnalyticsRecord record) {
        lock.lock();
        try {
            buffer.add(record);
            if (buffer.size() >= batchSize) {
                flushLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Buffers a list of records and flushes if the threshold is reached.
     *
     * @param records the records to buffer
     */
    public void batchInsert(List<AnalyticsRecord> records) {
        lock.lock();
        try {
            buffer.addAll(records);
            if (buffer.size() >= batchSize) {
                flushLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Scheduled flush
    // -------------------------------------------------------------------------

    /**
     * Time-based flush — triggered every {@code analytics.flush-interval-ms} ms.
     * Runs on the Spring scheduling thread; interleaves safely with Kafka threads
     * via the reentrant lock.
     */
    @Scheduled(fixedDelayString = "${analytics.flush-interval-ms:1000}")
    public void scheduledFlush() {
        lock.lock();
        try {
            if (!buffer.isEmpty()) {
                flushLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Drain remaining buffer on application shutdown. */
    @PreDestroy
    public void onShutdown() {
        scheduledFlush();
    }

    // -------------------------------------------------------------------------
    // Private helpers — caller must hold lock
    // -------------------------------------------------------------------------

    /**
     * Performs a JDBC batch update for all buffered records and clears the buffer.
     * Must be called while holding {@code lock}.
     */
    private void flushLocked() {
        if (buffer.isEmpty()) {
            return;
        }
        List<AnalyticsRecord> snapshot = new ArrayList<>(buffer);
        buffer.clear();

        List<Object[]> batchArgs = snapshot.stream()
                .map(r -> new Object[]{
                        r.txId(),
                        r.cardId(),
                        r.merchantId(),
                        r.amount(),
                        r.riskScore(),
                        r.decision(),
                        r.latencyMs(),
                        Timestamp.from(r.timestamp()),
                        r.country(),
                        r.channel(),
                        r.mcc()
                })
                .toList();

        jdbc.batchUpdate(INSERT_SQL, batchArgs);
        log.info("Flushed {} analytics records to ClickHouse", snapshot.size());
    }
}
