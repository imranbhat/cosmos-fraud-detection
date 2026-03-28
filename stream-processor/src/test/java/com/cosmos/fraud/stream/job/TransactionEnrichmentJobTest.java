package com.cosmos.fraud.stream.job;

import com.cosmos.fraud.stream.StreamProcessorJob;
import com.cosmos.fraud.stream.config.StreamConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Smoke-tests for {@link TransactionEnrichmentJob} pipeline construction.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Calling {@code buildPipeline} does not throw any exception.</li>
 *   <li>The Flink execution plan can be generated (validates operator wiring).</li>
 * </ul>
 *
 * <p>No Kafka broker or feature-store is required — the tests run in the local
 * Flink mini-environment and stop before {@code env.execute()} is called.
 */
class TransactionEnrichmentJobTest {

    // -----------------------------------------------------------------
    // Pipeline construction smoke tests
    // -----------------------------------------------------------------

    @Test
    @DisplayName("buildPipeline does not throw with default config")
    void buildPipeline_doesNotThrow_withDefaultConfig() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        StreamConfig config = StreamConfig.defaults();

        assertThatNoException().isThrownBy(
                () -> TransactionEnrichmentJob.buildPipeline(env, config));
    }

    @Test
    @DisplayName("buildPipeline produces a non-null execution plan")
    void buildPipeline_producesExecutionPlan() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        StreamConfig config = StreamConfig.defaults();

        TransactionEnrichmentJob.buildPipeline(env, config);

        String plan = env.getExecutionPlan();
        org.assertj.core.api.Assertions.assertThat(plan)
                .isNotNull()
                .isNotBlank()
                .contains("nodes");   // Flink JSON plan always has a "nodes" array
    }

    @Test
    @DisplayName("buildPipeline with custom kafka bootstrap does not throw")
    void buildPipeline_customKafkaBootstrap_doesNotThrow() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();

        // Simulate config that would be built from CLI args in production
        org.apache.flink.api.java.utils.ParameterTool params =
                org.apache.flink.api.java.utils.ParameterTool.fromMap(
                        java.util.Map.of(
                                StreamConfig.KEY_KAFKA_BOOTSTRAP,   "broker1:9092,broker2:9092",
                                StreamConfig.KEY_FEATURE_STORE_URL, "http://feature-store.svc:8080",
                                StreamConfig.KEY_ASYNC_CAPACITY,    "50"));
        StreamConfig config = StreamConfig.from(params);

        assertThatNoException().isThrownBy(
                () -> TransactionEnrichmentJob.buildPipeline(env, config));
    }

    // -----------------------------------------------------------------
    // StreamProcessorJob environment creation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("createEnvironment configures RocksDB backend without throwing")
    void createEnvironment_configuresRocksDb_withoutThrowing() {
        StreamConfig config = StreamConfig.defaults();

        assertThatNoException().isThrownBy(
                () -> StreamProcessorJob.createEnvironment(config));
    }

    @Test
    @DisplayName("createEnvironment returns non-null environment")
    void createEnvironment_returnsNonNullEnv() {
        StreamConfig config = StreamConfig.defaults();

        StreamExecutionEnvironment env = StreamProcessorJob.createEnvironment(config);

        org.assertj.core.api.Assertions.assertThat(env).isNotNull();
    }

    // -----------------------------------------------------------------
    // FraudAlertAggregationJob pipeline construction smoke test
    // -----------------------------------------------------------------

    @Test
    @DisplayName("FraudAlertAggregationJob.buildPipeline does not throw with default config")
    void fraudAlertAggregationJob_buildPipeline_doesNotThrow() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        StreamConfig config = StreamConfig.defaults();

        assertThatNoException().isThrownBy(
                () -> FraudAlertAggregationJob.buildPipeline(env, config));
    }

    @Test
    @DisplayName("Both pipelines can be attached to the same environment")
    void bothPipelines_canCoexistOnSameEnvironment() {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment();
        StreamConfig config = StreamConfig.defaults();

        assertThatNoException().isThrownBy(() -> {
            TransactionEnrichmentJob.buildPipeline(env, config);
            FraudAlertAggregationJob.buildPipeline(env, config);
        });
    }
}
