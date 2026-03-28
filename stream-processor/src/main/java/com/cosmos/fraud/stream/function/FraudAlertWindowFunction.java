package com.cosmos.fraud.stream.function;

import com.cosmos.fraud.stream.model.FraudDecision;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Flink {@link ProcessWindowFunction} that counts {@code DECLINE} fraud decisions
 * within a tumbling event-time window, keyed by {@code merchantId}.
 *
 * <p>If the decline count in a window exceeds the configurable threshold, an alert
 * JSON string is emitted to the {@code fraud.alerts} Kafka topic.
 *
 * <h3>Alert payload format</h3>
 * <pre>
 * {
 *   "alertType"   : "MERCHANT_DECLINE_SPIKE",
 *   "merchantId"  : "MID-12345",
 *   "windowStart" : "2024-01-15T10:00:00Z",
 *   "windowEnd"   : "2024-01-15T10:05:00Z",
 *   "declineCount": 15,
 *   "threshold"   : 10,
 *   "generatedAt" : "2024-01-15T10:05:01Z"
 * }
 * </pre>
 */
public final class FraudAlertWindowFunction
        extends ProcessWindowFunction<FraudDecision, String, String, TimeWindow> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(FraudAlertWindowFunction.class);

    private final int declineThreshold;

    // -----------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------

    /**
     * @param declineThreshold minimum number of DECLINE decisions in a window
     *                         that triggers an alert
     */
    public FraudAlertWindowFunction(int declineThreshold) {
        if (declineThreshold <= 0) {
            throw new IllegalArgumentException("declineThreshold must be positive, got: " + declineThreshold);
        }
        this.declineThreshold = declineThreshold;
    }

    // -----------------------------------------------------------------
    // ProcessWindowFunction implementation
    // -----------------------------------------------------------------

    /**
     * Called once per window per merchant key after the watermark passes the
     * window's end boundary.
     *
     * @param merchantId the key (merchantId)
     * @param context    window context (provides window boundaries)
     * @param elements   all {@link FraudDecision} records in the window
     * @param out        collector for alert strings
     */
    @Override
    public void process(
            String merchantId,
            Context context,
            Iterable<FraudDecision> elements,
            Collector<String> out) throws Exception {

        TimeWindow window = context.window();
        int totalCount   = 0;
        int declineCount = 0;

        for (FraudDecision decision : elements) {
            totalCount++;
            if (decision.isDecline()) {
                declineCount++;
            }
        }

        LOG.debug("Window [{} - {}] merchantId={} totalDecisions={} declines={}",
                Instant.ofEpochMilli(window.getStart()),
                Instant.ofEpochMilli(window.getEnd()),
                merchantId, totalCount, declineCount);

        if (declineCount > declineThreshold) {
            String alert = buildAlertJson(merchantId, window, declineCount);
            LOG.warn("FRAUD ALERT emitted: merchantId={} declines={} threshold={}",
                    merchantId, declineCount, declineThreshold);
            out.collect(alert);
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Builds the alert JSON manually to avoid a Jackson dependency in the
     * Flink operator (keeps the fat JAR lean). Replace with an ObjectMapper
     * if richer serialisation is needed.
     */
    private String buildAlertJson(String merchantId, TimeWindow window, int declineCount) {
        return "{"
                + "\"alertType\":\"MERCHANT_DECLINE_SPIKE\","
                + "\"merchantId\":\"" + escape(merchantId) + "\","
                + "\"windowStart\":\"" + Instant.ofEpochMilli(window.getStart()) + "\","
                + "\"windowEnd\":\"" + Instant.ofEpochMilli(window.getEnd()) + "\","
                + "\"declineCount\":" + declineCount + ","
                + "\"threshold\":" + declineThreshold + ","
                + "\"generatedAt\":\"" + Instant.now() + "\""
                + "}";
    }

    /** Minimal JSON string escape (handles quotes and backslashes). */
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public int getDeclineThreshold() {
        return declineThreshold;
    }
}
