package com.cosmos.fraud.stream.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Centralised configuration holder for the Cosmos Fraud stream-processor.
 * <p>
 * Configuration is read from command-line arguments (key=value pairs) or
 * a classpath properties file.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * StreamConfig config = StreamConfig.fromArgs(args);
 * }</pre>
 *
 * <h3>Supported keys (with defaults)</h3>
 * <ul>
 *   <li>{@code kafka.bootstrap.servers}  — default {@code localhost:9092}</li>
 *   <li>{@code kafka.schema.registry.url} — default {@code http://localhost:8081}</li>
 *   <li>{@code kafka.consumer.group.enrichment} — default {@code enrichment-group}</li>
 *   <li>{@code kafka.consumer.group.alerts}     — default {@code alerts-group}</li>
 *   <li>{@code feature.store.url}               — default {@code http://localhost:8080}</li>
 *   <li>{@code feature.store.timeout.ms}        — default {@code 200}</li>
 *   <li>{@code async.capacity}                  — default {@code 100}</li>
 *   <li>{@code alert.decline.threshold}         — default {@code 10}</li>
 *   <li>{@code checkpoint.interval.ms}          — default {@code 30000}</li>
 * </ul>
 */
public final class StreamConfig {

    // -----------------------------------------------------------------
    // Config keys
    // -----------------------------------------------------------------

    public static final String KEY_KAFKA_BOOTSTRAP        = "kafka.bootstrap.servers";
    public static final String KEY_SCHEMA_REGISTRY_URL    = "kafka.schema.registry.url";
    public static final String KEY_CONSUMER_GROUP_ENRICH  = "kafka.consumer.group.enrichment";
    public static final String KEY_CONSUMER_GROUP_ALERTS  = "kafka.consumer.group.alerts";
    public static final String KEY_FEATURE_STORE_URL      = "feature.store.url";
    public static final String KEY_FEATURE_STORE_TIMEOUT  = "feature.store.timeout.ms";
    public static final String KEY_ASYNC_CAPACITY         = "async.capacity";
    public static final String KEY_ALERT_DECLINE_THRESHOLD = "alert.decline.threshold";
    public static final String KEY_CHECKPOINT_INTERVAL_MS = "checkpoint.interval.ms";

    // -----------------------------------------------------------------
    // Default values
    // -----------------------------------------------------------------

    private static final String  DEFAULT_KAFKA_BOOTSTRAP        = "localhost:9092";
    private static final String  DEFAULT_SCHEMA_REGISTRY_URL    = "http://localhost:8081";
    private static final String  DEFAULT_CONSUMER_GROUP_ENRICH  = "enrichment-group";
    private static final String  DEFAULT_CONSUMER_GROUP_ALERTS  = "alerts-group";
    private static final String  DEFAULT_FEATURE_STORE_URL      = "http://localhost:8080";
    private static final long    DEFAULT_FEATURE_STORE_TIMEOUT  = 200L;
    private static final int     DEFAULT_ASYNC_CAPACITY         = 100;
    private static final int     DEFAULT_ALERT_DECLINE_THRESHOLD = 10;
    private static final long    DEFAULT_CHECKPOINT_INTERVAL_MS = 30_000L;

    // -----------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------

    private final String kafkaBootstrapServers;
    private final String schemaRegistryUrl;
    private final String enrichmentConsumerGroup;
    private final String alertsConsumerGroup;
    private final String featureStoreUrl;
    private final long   featureStoreTimeoutMs;
    private final int    asyncCapacity;
    private final int    alertDeclineThreshold;
    private final long   checkpointIntervalMs;

    // -----------------------------------------------------------------
    // Constructor (private — use factory methods)
    // -----------------------------------------------------------------

    private StreamConfig(
            String kafkaBootstrapServers,
            String schemaRegistryUrl,
            String enrichmentConsumerGroup,
            String alertsConsumerGroup,
            String featureStoreUrl,
            long featureStoreTimeoutMs,
            int asyncCapacity,
            int alertDeclineThreshold,
            long checkpointIntervalMs) {
        this.kafkaBootstrapServers  = kafkaBootstrapServers;
        this.schemaRegistryUrl      = schemaRegistryUrl;
        this.enrichmentConsumerGroup = enrichmentConsumerGroup;
        this.alertsConsumerGroup    = alertsConsumerGroup;
        this.featureStoreUrl        = featureStoreUrl;
        this.featureStoreTimeoutMs  = featureStoreTimeoutMs;
        this.asyncCapacity          = asyncCapacity;
        this.alertDeclineThreshold  = alertDeclineThreshold;
        this.checkpointIntervalMs   = checkpointIntervalMs;
    }

    // -----------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------

    /**
     * Creates a {@code StreamConfig} from a map of key-value pairs.
     *
     * @param params configuration map
     * @return fully populated config instance
     */
    public static StreamConfig from(Map<String, String> params) {
        return new StreamConfig(
                params.getOrDefault(KEY_KAFKA_BOOTSTRAP, DEFAULT_KAFKA_BOOTSTRAP),
                params.getOrDefault(KEY_SCHEMA_REGISTRY_URL, DEFAULT_SCHEMA_REGISTRY_URL),
                params.getOrDefault(KEY_CONSUMER_GROUP_ENRICH, DEFAULT_CONSUMER_GROUP_ENRICH),
                params.getOrDefault(KEY_CONSUMER_GROUP_ALERTS, DEFAULT_CONSUMER_GROUP_ALERTS),
                params.getOrDefault(KEY_FEATURE_STORE_URL, DEFAULT_FEATURE_STORE_URL),
                Long.parseLong(params.getOrDefault(KEY_FEATURE_STORE_TIMEOUT, String.valueOf(DEFAULT_FEATURE_STORE_TIMEOUT))),
                Integer.parseInt(params.getOrDefault(KEY_ASYNC_CAPACITY, String.valueOf(DEFAULT_ASYNC_CAPACITY))),
                Integer.parseInt(params.getOrDefault(KEY_ALERT_DECLINE_THRESHOLD, String.valueOf(DEFAULT_ALERT_DECLINE_THRESHOLD))),
                Long.parseLong(params.getOrDefault(KEY_CHECKPOINT_INTERVAL_MS, String.valueOf(DEFAULT_CHECKPOINT_INTERVAL_MS)))
        );
    }

    /**
     * Parses CLI arguments in {@code --key value} or {@code key=value} format
     * into a {@code StreamConfig}.
     *
     * @param args command-line arguments
     * @return fully populated config instance
     */
    public static StreamConfig fromArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.contains("=")) {
                String key = arg.startsWith("--") ? arg.substring(2, arg.indexOf('=')) : arg.substring(0, arg.indexOf('='));
                String value = arg.substring(arg.indexOf('=') + 1);
                params.put(key, value);
            } else if (arg.startsWith("--") && i + 1 < args.length) {
                params.put(arg.substring(2), args[++i]);
            }
        }
        return from(params);
    }

    /**
     * Creates a default config (useful for unit tests).
     */
    public static StreamConfig defaults() {
        return new StreamConfig(
                DEFAULT_KAFKA_BOOTSTRAP,
                DEFAULT_SCHEMA_REGISTRY_URL,
                DEFAULT_CONSUMER_GROUP_ENRICH,
                DEFAULT_CONSUMER_GROUP_ALERTS,
                DEFAULT_FEATURE_STORE_URL,
                DEFAULT_FEATURE_STORE_TIMEOUT,
                DEFAULT_ASYNC_CAPACITY,
                DEFAULT_ALERT_DECLINE_THRESHOLD,
                DEFAULT_CHECKPOINT_INTERVAL_MS
        );
    }

    // -----------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------

    public String getKafkaBootstrapServers()  { return kafkaBootstrapServers; }
    public String getSchemaRegistryUrl()       { return schemaRegistryUrl; }
    public String getEnrichmentConsumerGroup() { return enrichmentConsumerGroup; }
    public String getAlertsConsumerGroup()     { return alertsConsumerGroup; }
    public String getFeatureStoreUrl()         { return featureStoreUrl; }
    public long   getFeatureStoreTimeoutMs()   { return featureStoreTimeoutMs; }
    public int    getAsyncCapacity()           { return asyncCapacity; }
    public int    getAlertDeclineThreshold()   { return alertDeclineThreshold; }
    public long   getCheckpointIntervalMs()    { return checkpointIntervalMs; }

    @Override
    public String toString() {
        return "StreamConfig{"
                + "kafkaBootstrap='" + kafkaBootstrapServers + '\''
                + ", schemaRegistry='" + schemaRegistryUrl + '\''
                + ", featureStoreUrl='" + featureStoreUrl + '\''
                + ", alertDeclineThreshold=" + alertDeclineThreshold
                + ", checkpointIntervalMs=" + checkpointIntervalMs
                + '}';
    }
}
