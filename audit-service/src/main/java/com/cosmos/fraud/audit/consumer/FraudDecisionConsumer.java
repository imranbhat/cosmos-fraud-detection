package com.cosmos.fraud.audit.consumer;

import com.cosmos.fraud.audit.mapper.AuditMapper;
import com.cosmos.fraud.audit.model.AnalyticsRecord;
import com.cosmos.fraud.audit.model.AuditRecord;
import com.cosmos.fraud.audit.repository.AnalyticsRepository;
import com.cosmos.fraud.audit.repository.AuditRepository;
import com.cosmos.fraud.avro.FraudDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka consumer that processes fraud decisions and alerts, writing each event
 * to ScyllaDB for transactional audit and buffering it for ClickHouse analytics.
 *
 * <p>A correlation ID extracted from the {@code X-Correlation-Id} Kafka header
 * is placed into the SLF4J MDC for the duration of each message's processing so
 * that all downstream log statements carry the same trace identifier.
 */
@Component
public class FraudDecisionConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudDecisionConsumer.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_CORRELATION_KEY = "correlationId";

    private final AuditRepository auditRepository;
    private final AnalyticsRepository analyticsRepository;
    private final ObjectMapper objectMapper;

    public FraudDecisionConsumer(
            AuditRepository auditRepository,
            AnalyticsRepository analyticsRepository,
            ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.analyticsRepository = analyticsRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes messages from the {@code fraud.decisions} and {@code fraud.alerts} topics.
     *
     * @param record the raw Kafka consumer record carrying a {@link FraudDecision} payload
     */
    @KafkaListener(
            topics = {"fraud.decisions", "fraud.alerts"},
            groupId = "audit-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onFraudDecision(ConsumerRecord<String, FraudDecision> record) {
        String correlationId = extractCorrelationId(record);
        MDC.put(MDC_CORRELATION_KEY, correlationId);
        try {
            FraudDecision decision = record.value();
            log.info("Received fraud decision txId={} decision={} topic={} partition={} offset={}",
                    decision.getTxId(), decision.getDecision(),
                    record.topic(), record.partition(), record.offset());

            String rawEvent = serialiseToJson(decision);
            AuditRecord auditRecord = AuditMapper.fromFraudDecision(decision, rawEvent);

            // Persist to ScyllaDB — durable audit trail
            auditRepository.save(auditRecord);

            // Buffer for ClickHouse analytics
            AnalyticsRecord analyticsRecord = AuditMapper.toAnalyticsRecord(auditRecord);
            analyticsRepository.add(analyticsRecord);

            log.debug("Processed audit record txId={} correlationId={}", auditRecord.txId(), correlationId);
        } catch (Exception ex) {
            log.error("Failed to process fraud decision from topic={} offset={} correlationId={}",
                    record.topic(), record.offset(), correlationId, ex);
            // Re-throw so Spring Kafka's error handler / DLQ can handle retries.
            throw new RuntimeException("Audit processing failed for record at offset " + record.offset(), ex);
        } finally {
            MDC.remove(MDC_CORRELATION_KEY);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String extractCorrelationId(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(CORRELATION_HEADER);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "unknown-" + record.topic() + "-" + record.offset();
    }

    private String serialiseToJson(FraudDecision decision) {
        try {
            return objectMapper.writeValueAsString(decision);
        } catch (Exception ex) {
            log.warn("Could not serialise FraudDecision to JSON for txId={}", decision.getTxId(), ex);
            return "{}";
        }
    }
}
