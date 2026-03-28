package com.cosmos.fraud.stream.function;

import com.cosmos.fraud.stream.model.FraudDecision;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FraudAlertWindowFunction}.
 *
 * <p>Uses a simple list-backed {@link Collector} stub to capture output without
 * requiring a running Flink mini-cluster.
 */
class FraudAlertWindowFunctionTest {

    private static final int THRESHOLD          = 10;
    private static final String MERCHANT_ID     = "MID-TEST-001";

    /** 5-minute window: 10:00 → 10:05 UTC (epoch millis). */
    private static final long WINDOW_START_MS   = 1_700_000_000_000L;
    private static final long WINDOW_END_MS     = WINDOW_START_MS + (5 * 60 * 1_000L);

    private FraudAlertWindowFunction windowFunction;
    private ListCollector<String>    collector;
    private TimeWindow               window;
    private FraudAlertWindowFunction.Context context;

    @BeforeEach
    void setUp() {
        windowFunction = new FraudAlertWindowFunction(THRESHOLD);
        collector      = new ListCollector<>();
        window         = new TimeWindow(WINDOW_START_MS, WINDOW_END_MS);
        context        = new StubContext(window);
    }

    // -----------------------------------------------------------------
    // Happy-path: alert IS emitted
    // -----------------------------------------------------------------

    @Test
    @DisplayName("emits alert when decline count strictly exceeds threshold")
    void emitsAlert_whenDeclineCountExceedsThreshold() throws Exception {
        List<FraudDecision> decisions = buildDecisions(THRESHOLD + 1, "DECLINE");

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).hasSize(1);
        String alert = collector.items.get(0);
        assertThat(alert)
                .contains("\"alertType\":\"MERCHANT_DECLINE_SPIKE\"")
                .contains("\"merchantId\":\"" + MERCHANT_ID + "\"")
                .contains("\"declineCount\":" + (THRESHOLD + 1))
                .contains("\"threshold\":" + THRESHOLD);
    }

    @Test
    @DisplayName("alert JSON contains window start and end timestamps")
    void alertContainsWindowBoundaries() throws Exception {
        List<FraudDecision> decisions = buildDecisions(THRESHOLD + 5, "DECLINE");

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).hasSize(1);
        String alert = collector.items.get(0);
        // Instant.ofEpochMilli produces ISO-8601 — just check the key is present
        assertThat(alert)
                .contains("\"windowStart\"")
                .contains("\"windowEnd\"")
                .contains("\"generatedAt\"");
    }

    // -----------------------------------------------------------------
    // No alert cases
    // -----------------------------------------------------------------

    @Test
    @DisplayName("does NOT emit alert when decline count equals threshold (not strictly greater)")
    void doesNotEmitAlert_whenDeclineCountEqualsThreshold() throws Exception {
        List<FraudDecision> decisions = buildDecisions(THRESHOLD, "DECLINE");

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).isEmpty();
    }

    @Test
    @DisplayName("does NOT emit alert when all decisions are APPROVE")
    void doesNotEmitAlert_whenAllApprovals() throws Exception {
        List<FraudDecision> decisions = buildDecisions(50, "APPROVE");

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).isEmpty();
    }

    @Test
    @DisplayName("does NOT emit alert for empty window")
    void doesNotEmitAlert_forEmptyWindow() throws Exception {
        windowFunction.process(MERCHANT_ID, context, List.of(), collector);

        assertThat(collector.items).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 9})
    @DisplayName("does NOT emit alert when decline count is below threshold")
    void doesNotEmitAlert_belowThreshold(int declineCount) throws Exception {
        List<FraudDecision> decisions = buildDecisions(declineCount, "DECLINE");

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).isEmpty();
    }

    // -----------------------------------------------------------------
    // Mixed decisions
    // -----------------------------------------------------------------

    @Test
    @DisplayName("counts only DECLINE decisions — APPROVE and REVIEW are ignored")
    void countsMixedDecisions_onlyDeclines() throws Exception {
        // 8 declines + 5 approves + 3 reviews = 8 declines, threshold=10 → no alert
        List<FraudDecision> decisions = new ArrayList<>();
        decisions.addAll(buildDecisions(8,  "DECLINE"));
        decisions.addAll(buildDecisions(5,  "APPROVE"));
        decisions.addAll(buildDecisions(3,  "REVIEW"));

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).isEmpty();
    }

    @Test
    @DisplayName("emits alert with correct decline count from a mixed decision set")
    void emitsAlert_correctCount_fromMixedDecisions() throws Exception {
        // 12 declines + 20 approves → alert expected (12 > 10)
        List<FraudDecision> decisions = new ArrayList<>();
        decisions.addAll(buildDecisions(12, "DECLINE"));
        decisions.addAll(buildDecisions(20, "APPROVE"));

        windowFunction.process(MERCHANT_ID, context, decisions, collector);

        assertThat(collector.items).hasSize(1);
        assertThat(collector.items.get(0)).contains("\"declineCount\":12");
    }

    // -----------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("constructor rejects zero threshold")
    void constructor_rejectsZeroThreshold() {
        assertThatThrownBy(() -> new FraudAlertWindowFunction(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declineThreshold must be positive");
    }

    @Test
    @DisplayName("constructor rejects negative threshold")
    void constructor_rejectsNegativeThreshold() {
        assertThatThrownBy(() -> new FraudAlertWindowFunction(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static List<FraudDecision> buildDecisions(int count, String decision) {
        List<FraudDecision> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new FraudDecision(
                    "TX-" + i,
                    MERCHANT_ID,
                    decision,
                    decision.equals("DECLINE") ? 800 : 200,
                    WINDOW_START_MS + (i * 1_000L)));
        }
        return list;
    }

    // -----------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------

    /** Simple {@link Collector} that accumulates items in a list. */
    static final class ListCollector<T> implements Collector<T> {
        final List<T> items = new ArrayList<>();

        @Override public void collect(T record) { items.add(record); }
        @Override public void close() { /* no-op */ }
    }

    /**
     * Minimal {@link FraudAlertWindowFunction.Context} stub.
     * Only {@link #window()} is used by the function under test.
     */
    static final class StubContext extends FraudAlertWindowFunction.Context {

        private final TimeWindow window;

        StubContext(TimeWindow window) {
            this.window = window;
        }

        @Override
        public TimeWindow window() { return window; }

        @Override
        public long currentProcessingTime() { return System.currentTimeMillis(); }

        @Override
        public long currentWatermark() { return window.getEnd(); }

        @Override
        public <X> org.apache.flink.util.OutputTag<X> outputTag() { return null; }

        @Override
        public <X> void output(org.apache.flink.util.OutputTag<X> outputTag, X value) { /* no-op */ }
    }
}
