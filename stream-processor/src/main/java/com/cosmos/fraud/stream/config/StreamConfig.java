package com.cosmos.fraud.stream.config;

import org.apache.flink.api.java.utils.ParameterTool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralised configuration holder for the Cosmos Fraud stream-processor.
 * <p>
 * Configuration is read from Flink's {@link ParameterTool}, which unifies
 * command-line arguments, system properties, and properties files.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * ParameterTool params = ParameterTool.fromArgs(args)
 *         .mergeWith(ParameterTool.fromPropertiesFile("stream-processor.properties"));
 * StreamConfig config = StreamConfig.from(params);
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
     * Creates a {@code StreamConfig} from a Flink {@link ParameterTool}.
     *
     * @param params Flink parameter tool (merged from CLI args + properties file)
     * @return fully populated config instance
     */
    public static StreamConfig from(ParameterTool params) {
        return new StreamConfig(
                params.get(KEY_KAFKA_BOOTSTRAP, DEFAULT_KAFKA_BOOTSTRAP),
                params.get(KEY_SCHEMA_REGISTRY_URL, DEFAULT_SCHEMA_REGISTRY_URL),
                params.get(KEY_CONSUMER_GROUP_ENRICH, DEFAULT_CONSUMER_GROUP_ENRICH),
                params.get(KEY_CONSUMER_GROUP_ALERTS, DEFAULT_CONSUMER_GROUP_ALERTS),
                params.get(KEY_FEATURE_STORE_URL, DEFAULT_FEATURE_STORE_URL),
                params.getLong(KEY_FEATURE_STORE_TIMEOUT, DEFAULT_FEATURE_STORE_TIMEOUT),
                params.getInt(KEY_ASYNC_CAPACITY, DEFAULT_ASYNC_CAPACITY),
                params.getInt(KEY_ALERT_DECLINE_THRESHOLD, DEFAULT_ALERT_DECLINE_THRESHOLD),
                params.getLong(KEY_CHECKPOINT_INTERVAL_MS, DEFAULT_CHECKPOINT_INTERVAL_MS)
        );
    }

    /**
     * Creates a {@code StreamConfig} by merging CLI args with a properties file
     * loaded from the classpath.
     *
     * @param args           command-line arguments passed to {@code main()}
     * @param propertiesFile classpath resource name (e.g. "stream-processor.properties")
     * @return fully populated config instance
     * @throws IOException if the properties file cannot be read
     */
    public static StreamConfig fromArgsAndClasspath(String[] args, String propertiesFile)
            throws IOException {
        ParameterTool fromArgs = ParameterTool.fromArgs(args);
        Properties props = new Properties();
        try (InputStream is = StreamConfig.class.getClassLoader()
                .getResourceAsStream(propertiesFile)) {
            if (is != null) {
                props.load(is);
            }
        }
        ParameterTool merged = ParameterTool.fromPropertiesFile(
                propertiesFile.startsWith("/") ? propertiesFile : propertiesFile)
                .mergeWith(fromArgs);
        return from(merged);
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
