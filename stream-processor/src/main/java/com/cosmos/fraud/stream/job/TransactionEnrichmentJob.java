package com.cosmos.fraud.stream.job;

import com.cosmos.fraud.stream.config.StreamConfig;
import com.cosmos.fraud.stream.function.FeatureEnrichmentAsyncFunction;
import com.cosmos.fraud.stream.model.EnrichedTransaction;
import com.cosmos.fraud.stream.model.TransactionEvent;
import com.cosmos.fraud.stream.serialization.AvroDeserializationSchema;
import com.cosmos.fraud.stream.serialization.AvroSerializationSchema;
import com.cosmos.fraud.common.kafka.KafkaTopics;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Flink job that reads raw transactions, enriches them with feature-store data
 * and publishes the enriched records to downstream Kafka topics.
 *
 * <h3>Pipeline topology</h3>
 * <pre>
 *  KafkaSource(transactions.raw)
 *       │
 *       ▼
 *  AsyncDataStream.unorderedWait   ← FeatureEnrichmentAsyncFunction
 *       │
 *       ├──► KafkaSink(transactions.enriched)    [main output]
 *       └──► KafkaSink(features.updates)         [side output / pass-through]
 * </pre>
 *
 * <p>Call {@link #buildPipeline(StreamExecutionEnvironment, StreamConfig)} to
 * attach the pipeline to an existing environment without executing it. This makes
 * the job composable and unit-testable.
 */
public final class TransactionEnrichmentJob {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionEnrichmentJob.class);

    /**
     * Side-output tag used to route enriched records to the {@code features.updates}
     * topic as well as the primary enriched topic.
     */
    public static final OutputTag<EnrichedTransaction> FEATURES_UPDATE_TAG =
            new OutputTag<EnrichedTransaction>("features-updates") {};

    private TransactionEnrichmentJob() {
        // utility class — not instantiated
    }

    // -----------------------------------------------------------------
    // Pipeline factory
    // -----------------------------------------------------------------

    /**
     * Builds and wires the enrichment pipeline onto the given
     * {@link StreamExecutionEnvironment}.
     *
     * <p>Does <em>not</em> call {@code env.execute()} — that is the caller's
     * responsibility.
     *
     * @param env    the Flink execution environment
     * @param config the resolved stream-processor configuration
     */
    public static void buildPipeline(StreamExecutionEnvironment env, StreamConfig config) {

        LOG.info("Building TransactionEnrichmentJob pipeline");

        // ---- 1. Kafka source -----------------------------------------------
        KafkaSource<TransactionEvent> kafkaSource = KafkaSource.<TransactionEvent>builder()
                .setBootstrapServers(config.getKafkaBootstrapServers())
                .setTopics(KafkaTopics.TRANSACTIONS_RAW)
                .setGroupId(config.getEnrichmentConsumerGroup())
                .setStartingOffsets(OffsetsInitializer.committedOffsets(
                        org.apache.kafka.clients.consumer.OffsetResetStrategy.LATEST))
                .setValueOnlyDeserializer(new AvroDeserializationSchema())
                .build();

        DataStream<TransactionEvent> rawStream = env
                .fromSource(kafkaSource,
                        WatermarkStrategy.<TransactionEvent>forBoundedOutOfOrderness(
                                        Duration.ofSeconds(5))
                                .withTimestampAssigner(
                                        (event, ts) -> event.getEventTimestamp()),
                        "kafka-source-transactions-raw");

        // ---- 2. Async feature enrichment -----------------------------------
        FeatureEnrichmentAsyncFunction enrichFn = new FeatureEnrichmentAsyncFunction(
                config.getFeatureStoreUrl(), config.getFeatureStoreTimeoutMs());

        DataStream<EnrichedTransaction> enrichedStream = AsyncDataStream.unorderedWait(
                rawStream,
                enrichFn,
                config.getFeatureStoreTimeoutMs() * 2,   // outer timeout = 2 × feature-store timeout
                TimeUnit.MILLISECONDS,
                config.getAsyncCapacity());

        // ---- 3. Sink: transactions.enriched ---------------------------------
        KafkaSink<EnrichedTransaction> enrichedSink = KafkaSink.<EnrichedTransaction>builder()
                .setBootstrapServers(config.getKafkaBootstrapServers())
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<EnrichedTransaction>builder()
                                .setTopic(KafkaTopics.TRANSACTIONS_ENRICHED)
                                .setValueSerializationSchema(new AvroSerializationSchema())
                                .build())
                .build();

        enrichedStream.sinkTo(enrichedSink).name("kafka-sink-transactions-enriched");

        // ---- 4. Sink: features.updates (pass-through for feature consumers) --
        //
        // The enriched stream is also routed to the features.updates topic so
        // that downstream consumers (e.g. the feature-store updater) can process
        // it without needing to read from transactions.enriched.  We reuse the
        // same serialisation schema.
        KafkaSink<EnrichedTransaction> featuresUpdateSink = KafkaSink.<EnrichedTransaction>builder()
                .setBootstrapServers(config.getKafkaBootstrapServers())
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<EnrichedTransaction>builder()
                                .setTopic(KafkaTopics.FEATURES_UPDATES)
                                .setValueSerializationSchema(new AvroSerializationSchema())
                                .build())
                .build();

        enrichedStream.sinkTo(featuresUpdateSink).name("kafka-sink-features-updates");

        LOG.info("TransactionEnrichmentJob pipeline built successfully");
    }
}
