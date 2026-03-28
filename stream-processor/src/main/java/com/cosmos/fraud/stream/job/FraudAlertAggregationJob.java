package com.cosmos.fraud.stream.job;

import com.cosmos.fraud.stream.config.StreamConfig;
import com.cosmos.fraud.stream.function.FraudAlertWindowFunction;
import com.cosmos.fraud.stream.model.FraudDecision;
import com.cosmos.fraud.common.kafka.KafkaTopics;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Flink job that aggregates fraud decisions per merchant in 5-minute tumbling
 * event-time windows and emits an alert whenever the DECLINE count exceeds a
 * configurable threshold.
 *
 * <h3>Pipeline topology</h3>
 * <pre>
 *  KafkaSource(fraud.decisions)
 *       │
 *       ▼
 *  keyBy(merchantId)
 *       │
 *       ▼
 *  TumblingEventTimeWindows(5 min)
 *       │
 *       ▼
 *  FraudAlertWindowFunction   ← counts DECLINE, emits alert if > threshold
 *       │
 *       ▼
 *  KafkaSink(fraud.alerts)
 * </pre>
 *
 * <p>Call {@link #buildPipeline(StreamExecutionEnvironment, StreamConfig)} to
 * attach the pipeline to an existing environment without executing it.
 */
public final class FraudAlertAggregationJob {

    private static final Logger LOG = LoggerFactory.getLogger(FraudAlertAggregationJob.class);

    /** Tumbling window size for merchant-level decline aggregation. */
    private static final Time WINDOW_SIZE = Time.minutes(5);

    private FraudAlertAggregationJob() {
        // utility class — not instantiated
    }

    // -----------------------------------------------------------------
    // Pipeline factory
    // -----------------------------------------------------------------

    /**
     * Builds and wires the alert-aggregation pipeline onto the given
     * {@link StreamExecutionEnvironment}.
     *
     * <p>Does <em>not</em> call {@code env.execute()}.
     *
     * @param env    the Flink execution environment
     * @param config the resolved stream-processor configuration
     */
    public static void buildPipeline(StreamExecutionEnvironment env, StreamConfig config) {

        LOG.info("Building FraudAlertAggregationJob pipeline");

        // ---- 1. Kafka source -----------------------------------------------
        KafkaSource<FraudDecision> kafkaSource = KafkaSource.<FraudDecision>builder()
                .setBootstrapServers(config.getKafkaBootstrapServers())
                .setTopics(KafkaTopics.FRAUD_DECISIONS)
                .setGroupId(config.getAlertsConsumerGroup())
                .setStartingOffsets(OffsetsInitializer.committedOffsets(
                        org.apache.kafka.clients.consumer.OffsetResetStrategy.LATEST))
                .setValueOnlyDeserializer(new FraudDecisionAvroDeserializer())
                .build();

        DataStream<FraudDecision> decisionsStream = env
                .fromSource(kafkaSource,
                        WatermarkStrategy.<FraudDecision>forBoundedOutOfOrderness(
                                        Duration.ofSeconds(10))
                                .withTimestampAssigner(
                                        (decision, ts) -> decision.getDecisionTimestamp()),
                        "kafka-source-fraud-decisions");

        // ---- 2. Key by merchantId, tumbling window, process ----------------
        DataStream<String> alertsStream = decisionsStream
                .keyBy(FraudDecision::getMerchantId)
                .window(TumblingEventTimeWindows.of(WINDOW_SIZE))
                .process(new FraudAlertWindowFunction(config.getAlertDeclineThreshold()))
                .name("fraud-alert-aggregation-window");

        // ---- 3. Sink: fraud.alerts -----------------------------------------
        KafkaSink<String> alertsSink = KafkaSink.<String>builder()
                .setBootstrapServers(config.getKafkaBootstrapServers())
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<String>builder()
                                .setTopic(KafkaTopics.FRAUD_ALERTS)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build())
                .build();

        alertsStream.sinkTo(alertsSink).name("kafka-sink-fraud-alerts");

        LOG.info("FraudAlertAggregationJob pipeline built successfully");
    }

    // -----------------------------------------------------------------
    // Inner: Avro deserialiser for FraudDecision
    // -----------------------------------------------------------------

    /**
     * Inline Avro deserialiser for {@link FraudDecision}.
     * <p>
     * Placed here as a private static class to keep the Avro dependency
     * localised — avoids polluting the top-level package with a
     * single-use schema class.
     */
    static final class FraudDecisionAvroDeserializer implements DeserializationSchema<FraudDecision> {

        private static final long serialVersionUID = 1L;

        private transient ReflectDatumReader<FraudDecision> datumReader;
        private transient BinaryDecoder decoder;

        @Override
        public void open(InitializationContext context) {
            datumReader = new ReflectDatumReader<>(FraudDecision.class);
        }

        @Override
        public FraudDecision deserialize(byte[] message) throws IOException {
            if (datumReader == null) {
                datumReader = new ReflectDatumReader<>(FraudDecision.class);
            }
            decoder = DecoderFactory.get().binaryDecoder(message, decoder);
            return datumReader.read(null, decoder);
        }

        @Override
        public boolean isEndOfStream(FraudDecision nextElement) {
            return false;
        }

        @Override
        public TypeInformation<FraudDecision> getProducedType() {
            return TypeInformation.of(FraudDecision.class);
        }
    }
}
