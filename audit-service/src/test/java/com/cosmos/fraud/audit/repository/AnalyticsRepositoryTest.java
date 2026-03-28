package com.cosmos.fraud.audit.repository;

import com.cosmos.fraud.audit.model.AnalyticsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AnalyticsRepository}.
 *
 * <p>The ClickHouse {@link JdbcTemplate} is mocked so no real database is needed.
 * Tests verify:
 * <ol>
 *   <li>Batch INSERT SQL and parameter structure</li>
 *   <li>Flush triggered when buffer reaches the batch-size threshold</li>
 *   <li>Time-based flush ({@code scheduledFlush}) empties remaining records</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsRepositoryTest {

    private static final int BATCH_SIZE = 5;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<List<Object[]>> batchArgsCaptor;

    private AnalyticsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AnalyticsRepository(jdbcTemplate, BATCH_SIZE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AnalyticsRecord record(String txId) {
        return new AnalyticsRecord(
                txId, "card-1", "merch-1",
                100.0, 50, "APPROVE",
                30L, Instant.parse("2024-01-01T00:00:00Z"),
                "US", "ECOM", "5411"
        );
    }

    // -------------------------------------------------------------------------
    // Tests: flush on size threshold
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchInsert flushes when buffer reaches batch-size threshold")
    void batchInsert_flushesOnSizeThreshold() {
        List<AnalyticsRecord> records = IntStream.range(0, BATCH_SIZE)
                .mapToObj(i -> record("tx-" + i))
                .toList();

        repository.batchInsert(records);

        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("batchInsert does NOT flush when buffer is below batch-size")
    void batchInsert_doesNotFlushBelowThreshold() {
        List<AnalyticsRecord> records = IntStream.range(0, BATCH_SIZE - 1)
                .mapToObj(i -> record("tx-" + i))
                .toList();

        repository.batchInsert(records);

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("add triggers flush exactly once when the BATCH_SIZE-th record is added")
    void add_triggersFlushAtBatchSize() {
        // Add BATCH_SIZE - 1 records without triggering flush
        IntStream.range(0, BATCH_SIZE - 1).forEach(i -> repository.add(record("tx-" + i)));
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));

        // The BATCH_SIZE-th record should trigger the flush
        repository.add(record("tx-final"));

        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), any(List.class));
    }

    // -------------------------------------------------------------------------
    // Tests: SQL and parameter structure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("batchUpdate receives correct INSERT SQL with 11 columns")
    void batchInsert_correctSql() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        List<AnalyticsRecord> records = IntStream.range(0, BATCH_SIZE)
                .mapToObj(i -> record("tx-" + i))
                .toList();

        repository.batchInsert(records);

        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), any(List.class));
        String sql = sqlCaptor.getValue();
        assertThat(sql).containsIgnoringCase("INSERT INTO transactions_analytics");
        assertThat(sql).contains("tx_id", "card_id", "merchant_id", "amount", "risk_score",
                "decision", "latency_ms", "timestamp", "country", "channel", "mcc");
    }

    @Test
    @DisplayName("each batch row has exactly 11 parameters")
    void batchInsert_eachRowHas11Params() {
        List<AnalyticsRecord> records = IntStream.range(0, BATCH_SIZE)
                .mapToObj(i -> record("tx-" + i))
                .toList();

        repository.batchInsert(records);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> captor = (ArgumentCaptor<List<Object[]>>) (ArgumentCaptor<?>) batchArgsCaptor;
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture());

        List<Object[]> batchArgs = captor.getValue();
        assertThat(batchArgs).hasSize(BATCH_SIZE);
        batchArgs.forEach(row -> assertThat(row).hasSize(11));
    }

    @Test
    @DisplayName("first param in each batch row is the txId string")
    void batchInsert_firstParamIsTxId() {
        AnalyticsRecord r = record("tx-abc");
        List<AnalyticsRecord> records = List.of(r, r, r, r, r); // exactly BATCH_SIZE

        repository.batchInsert(records);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> captor = (ArgumentCaptor<List<Object[]>>) (ArgumentCaptor<?>) batchArgsCaptor;
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture());

        captor.getValue().forEach(row -> assertThat(row[0]).isEqualTo("tx-abc"));
    }

    // -------------------------------------------------------------------------
    // Tests: scheduled flush
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("scheduledFlush flushes non-empty buffer")
    void scheduledFlush_flushesBuffer() {
        // Add 2 records (below BATCH_SIZE=5), no auto-flush
        repository.add(record("tx-1"));
        repository.add(record("tx-2"));
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));

        // Simulate timer tick
        repository.scheduledFlush();

        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), any(List.class));
    }

    @Test
    @DisplayName("scheduledFlush is a no-op when buffer is empty")
    void scheduledFlush_noOpOnEmptyBuffer() {
        repository.scheduledFlush();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
    }
}
