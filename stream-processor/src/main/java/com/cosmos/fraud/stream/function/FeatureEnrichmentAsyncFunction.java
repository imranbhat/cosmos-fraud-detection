package com.cosmos.fraud.stream.function;

import com.cosmos.fraud.stream.model.EnrichedTransaction;
import com.cosmos.fraud.stream.model.TransactionEvent;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Flink {@link RichAsyncFunction} that enriches every {@link TransactionEvent}
 * with real-time features from the feature-store REST API.
 *
 * <h3>Feature store calls per event</h3>
 * <ol>
 *   <li><strong>GET /v1/features/{cardId}</strong> — fetch the current feature
 *       snapshot for the card (velocity counters, risk indicators, etc.).</li>
 *   <li><strong>POST /v1/features/{cardId}/update</strong> — write the current
 *       transaction back to the feature store so that aggregates are kept fresh.</li>
 * </ol>
 *
 * <p>Both calls are issued concurrently via {@link CompletableFuture#allOf} to
 * minimise latency. A timeout is configured on the outer
 * {@code AsyncDataStream.unorderedWait} call.
 *
 * <p>On error (non-2xx response, network failure, or timeout), the function emits
 * the event without feature enrichment (feature map is empty) so that no records
 * are dropped — the downstream scoring stage can handle the missing features
 * gracefully.
 */
public final class FeatureEnrichmentAsyncFunction
        extends RichAsyncFunction<TransactionEvent, EnrichedTransaction> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(FeatureEnrichmentAsyncFunction.class);

    private static final String DEFAULT_URL        = "http://localhost:8080";
    private static final long   DEFAULT_TIMEOUT_MS = 200L;

    private final String featureStoreBaseUrl;
    private final long   timeoutMs;

    // Transient — recreated after deserialisation / task restore
    private transient HttpClient httpClient;

    // -----------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------

    public FeatureEnrichmentAsyncFunction(String featureStoreBaseUrl, long timeoutMs) {
        this.featureStoreBaseUrl = featureStoreBaseUrl;
        this.timeoutMs = timeoutMs;
    }

    public FeatureEnrichmentAsyncFunction() {
        this(DEFAULT_URL, DEFAULT_TIMEOUT_MS);
    }

    // -----------------------------------------------------------------
    // RichAsyncFunction lifecycle
    // -----------------------------------------------------------------

    /**
     * Called once per Flink task slot before the first record is processed.
     * Initialises the shared HTTP client and JSON mapper.
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        LOG.info("FeatureEnrichmentAsyncFunction opened. featureStoreUrl={}", featureStoreBaseUrl);
    }

    @Override
    public void close() throws Exception {
        super.close();
        // HttpClient is closed automatically (Java 21 implements Closeable)
    }

    // -----------------------------------------------------------------
    // Async invocation
    // -----------------------------------------------------------------

    /**
     * Fires two non-blocking HTTP requests in parallel:
     * <ul>
     *   <li>GET to fetch the current feature snapshot for the card</li>
     *   <li>POST to update the feature store with the current transaction</li>
     * </ul>
     * The {@link ResultFuture} is completed with the merged result after both
     * futures resolve.
     */
    @Override
    public void asyncInvoke(TransactionEvent event, ResultFuture<EnrichedTransaction> resultFuture)
            throws Exception {

        long startNs = System.nanoTime();
        String cardId = event.getCardId();

        // ---- GET /v1/features/{cardId} ----
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(featureStoreBaseUrl + "/v1/features/" + cardId))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .header("Accept", "application/json")
                .build();

        CompletableFuture<HttpResponse<String>> getFuture = httpClient
                .sendAsync(getRequest, HttpResponse.BodyHandlers.ofString());

        // ---- POST /v1/features/{cardId}/update ----
        String updateBody = buildUpdatePayload(event);
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(featureStoreBaseUrl + "/v1/features/" + cardId + "/update"))
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(updateBody))
                .header("Content-Type", "application/json")
                .build();

        CompletableFuture<HttpResponse<String>> postFuture = httpClient
                .sendAsync(postRequest, HttpResponse.BodyHandlers.ofString());

        // ---- Combine and complete ResultFuture ----
        CompletableFuture.allOf(getFuture, postFuture).whenComplete((ignored, ex) -> {
            EnrichedTransaction enriched = EnrichedTransaction.from(event);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            enriched.setFeatureLatencyMs(latencyMs);

            if (ex != null) {
                LOG.warn("Feature-store call failed for cardId={} txId={}: {}",
                        cardId, event.getTxId(), ex.getMessage());
                // Emit without features — downstream handles absent enrichment
                resultFuture.complete(Collections.singletonList(enriched));
                return;
            }

            try {
                HttpResponse<String> getResponse = getFuture.join();
                if (getResponse.statusCode() >= 200 && getResponse.statusCode() < 300) {
                    Map<String, Object> features = parseFeatures(getResponse.body());
                    enriched.setFeatures(features);
                } else {
                    LOG.warn("Feature GET returned status={} for cardId={}", getResponse.statusCode(), cardId);
                }
            } catch (Exception parseEx) {
                LOG.warn("Failed to parse feature response for cardId={}: {}", cardId, parseEx.getMessage());
            }

            resultFuture.complete(Collections.singletonList(enriched));
        });
    }

    /**
     * Called when the outer {@code AsyncDataStream} timeout fires before the
     * async operation completes. Emits the event with an empty feature map so
     * the record is not lost.
     */
    @Override
    public void timeout(TransactionEvent input, ResultFuture<EnrichedTransaction> resultFuture)
            throws Exception {
        LOG.warn("Feature-store call timed out for cardId={} txId={}",
                input.getCardId(), input.getTxId());
        EnrichedTransaction enriched = EnrichedTransaction.from(input);
        enriched.setFeatures(Collections.emptyMap());
        resultFuture.complete(Collections.singletonList(enriched));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Builds a simple JSON payload for the POST /v1/features/{cardId}/update call.
     * Uses manual string building to avoid a Jackson runtime dependency in the
     * fat JAR (Jackson is not guaranteed to be present in a Flink cluster's lib/).
     */
    private String buildUpdatePayload(TransactionEvent event) {
        String amount = event.getAmount() != null ? event.getAmount().toPlainString() : "0";
        return "{"
                + "\"txId\":\""         + esc(event.getTxId())        + "\","
                + "\"merchantId\":\""   + esc(event.getMerchantId())   + "\","
                + "\"amount\":\""       + amount                       + "\","
                + "\"currency\":\""     + esc(event.getCurrency())     + "\","
                + "\"mcc\":\""          + esc(event.getMcc())          + "\","
                + "\"channel\":\""      + esc(event.getChannel())      + "\","
                + "\"country\":\""      + esc(event.getCountry())      + "\","
                + "\"eventTimestamp\":" + event.getEventTimestamp()
                + "}";
    }

    /**
     * Parses a flat JSON object {@code {"key":"value",...}} into a {@link Map}.
     *
     * <p>This is a best-effort parser sufficient for feature maps returned by
     * the feature-store. For production use with nested structures, replace this
     * with a proper JSON library available in the Flink cluster's lib/ directory.
     */
    private Map<String, Object> parseFeatures(String json) {
        if (json == null || json.isBlank() || json.trim().equals("{}")) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new HashMap<>();
        // Strip outer braces
        String body = json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}"))   body = body.substring(0, body.length() - 1);

        // Split on top-level commas (simple: does not handle nested objects/arrays)
        for (String pair : body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key   = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
            String value = pair.substring(colon + 1).trim().replaceAll("^\"|\"$", "");
            map.put(key, value);
        }
        return map;
    }

    /** Minimal JSON string escape. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
