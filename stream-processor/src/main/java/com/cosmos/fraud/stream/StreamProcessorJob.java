package com.cosmos.fraud.stream;

import com.cosmos.fraud.stream.config.StreamConfig;
import com.cosmos.fraud.stream.job.FraudAlertAggregationJob;
import com.cosmos.fraud.stream.job.TransactionEnrichmentJob;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.StateBackendOptions;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Cosmos Fraud Detection stream-processing job.
 *
 * <p>This class is referenced in the shade manifest's {@code Main-Class} attribute
 * and is therefore the executable entry point when the fat JAR is submitted to a
 * Flink cluster:
 * <pre>
 *   flink run cosmos-stream-processor.jar \
 *     --kafka.bootstrap.servers kafka:9092 \
 *     --feature.store.url http://feature-store:8080
 * </pre>
 *
 * <h3>Environment setup</h3>
 * <ul>
 *   <li>State backend: RocksDB (incremental checkpoints)</li>
 *   <li>Checkpointing: exactly-once every 30 seconds</li>
 *   <li>Minimum pause between checkpoints: 5 seconds</li>
 *   <li>Checkpoint timeout: 60 seconds</li>
 *   <li>Concurrent checkpoints: 1</li>
 *   <li>Externalized checkpoints: retained on cancellation</li>
 * </ul>
 *
 * <h3>Pipelines launched</h3>
 * <ol>
 *   <li>{@link TransactionEnrichmentJob} — raw → enriched</li>
 *   <li>{@link FraudAlertAggregationJob} — decisions → alerts</li>
 * </ol>
 */
public final class StreamProcessorJob {

    private static final Logger LOG = LoggerFactory.getLogger(StreamProcessorJob.class);

    private static final String JOB_NAME = "cosmos-fraud-stream-processor";

    private StreamProcessorJob() {
        // not instantiated
    }

    // -----------------------------------------------------------------
    // main
    // -----------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        LOG.info("Starting {}", JOB_NAME);

        // ---- 1. Resolve configuration from CLI args ----------------------
        StreamConfig config = StreamConfig.fromArgs(args);
        LOG.info("Resolved config: {}", config);

        // ---- 2. Create and configure the execution environment -----------
        StreamExecutionEnvironment env = createEnvironment(config);

        // ---- 3. Build pipelines ------------------------------------------
        TransactionEnrichmentJob.buildPipeline(env, config);
        FraudAlertAggregationJob.buildPipeline(env, config);

        // ---- 4. Execute (blocking in cluster mode) -----------------------
        LOG.info("Submitting Flink job: {}", JOB_NAME);
        env.execute(JOB_NAME);
    }

    // -----------------------------------------------------------------
    // Environment factory (package-private for testability)
    // -----------------------------------------------------------------

    /**
     * Creates a fully configured {@link StreamExecutionEnvironment}.
     *
     * <p>Exposed with package-private visibility so that integration tests can
     * inspect the environment configuration without triggering job execution.
     *
     * @param config the resolved stream configuration
     * @return configured environment
     */
    public static StreamExecutionEnvironment createEnvironment(StreamConfig config) {
        // Configure RocksDB state backend via Configuration (Flink 2.x API)
        Configuration flinkConfig = new Configuration();
        flinkConfig.setString(StateBackendOptions.STATE_BACKEND.key(), "rocksdb");
        flinkConfig.setString("state.backend.incremental", "true");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(flinkConfig);

        // ---- Checkpointing -----------------------------------------------
        long checkpointInterval = config.getCheckpointIntervalMs();   // default 30 000 ms
        env.enableCheckpointing(checkpointInterval, CheckpointingMode.EXACTLY_ONCE);

        CheckpointConfig cpConfig = env.getCheckpointConfig();

        // Minimum time between checkpoints (5 s) to avoid checkpoint storms
        cpConfig.setMinPauseBetweenCheckpoints(5_000L);

        // Checkpoint must complete within 60 s
        cpConfig.setCheckpointTimeout(60_000L);

        // Only one checkpoint at a time
        cpConfig.setMaxConcurrentCheckpoints(1);

        // Retain checkpoints on job cancellation so we can resume from them
        flinkConfig.setString("execution.checkpointing.externalized-checkpoint-retention",
                "RETAIN_ON_CANCELLATION");

        LOG.info("Flink environment configured: checkpointInterval={}ms, backend=RocksDB",
                checkpointInterval);

        return env;
    }
}
