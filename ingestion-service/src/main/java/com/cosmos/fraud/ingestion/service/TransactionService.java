package com.cosmos.fraud.ingestion.service;

import com.cosmos.fraud.avro.FraudDecision;
import com.cosmos.fraud.avro.TransactionEvent;
import com.cosmos.fraud.common.dto.ScoringResponse;
import com.cosmos.fraud.common.dto.TransactionRequest;
import com.cosmos.fraud.common.exception.ScoringTimeoutException;
import com.cosmos.fraud.common.kafka.KafkaTopics;
import com.cosmos.fraud.common.util.IdGenerator;
import com.cosmos.fraud.ingestion.mapper.TransactionMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final long TIMEOUT_MS = 45L;

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final ReplyingKafkaTemplate<String, SpecificRecord, SpecificRecord> replyingKafkaTemplate;

    public TransactionService(
            KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            ReplyingKafkaTemplate<String, SpecificRecord, SpecificRecord> replyingKafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.replyingKafkaTemplate = replyingKafkaTemplate;
    }

    @CircuitBreaker(name = "scoring", fallbackMethod = "scoringFallback")
    public ScoringResponse processTransaction(TransactionRequest request) {
        String txId = IdGenerator.generateTxId();
        MDC.put("txId", txId);
        MDC.put("cardId", request.cardId());

        try {
            log.debug("Processing transaction txId={} cardId={}", txId, request.cardId());

            TransactionEvent event = TransactionMapper.toAvroEvent(request, txId);

            kafkaTemplate.send(KafkaTopics.TRANSACTIONS_RAW, txId, event);
            log.debug("Published TransactionEvent to {} for txId={}", KafkaTopics.TRANSACTIONS_RAW, txId);

            ProducerRecord<String, SpecificRecord> producerRecord =
                    new ProducerRecord<>(KafkaTopics.TRANSACTIONS_RAW, txId, event);

            RequestReplyFuture<String, SpecificRecord, SpecificRecord> replyFuture =
                    replyingKafkaTemplate.sendAndReceive(producerRecord);

            long startTime = System.currentTimeMillis();
            ConsumerRecord<String, SpecificRecord> consumerRecord =
                    replyFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Received scoring reply for txId={} in {}ms", txId, elapsed);

            FraudDecision decision = (FraudDecision) consumerRecord.value();
            return TransactionMapper.toScoringResponse(decision);

        } catch (TimeoutException ex) {
            long elapsed = TIMEOUT_MS;
            log.warn("Scoring timeout for txId={} after {}ms", txId, elapsed);
            throw new ScoringTimeoutException(elapsed, ex);
        } catch (Exception ex) {
            log.error("Error processing transaction txId={}: {}", txId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to process transaction: " + ex.getMessage(), ex);
        } finally {
            MDC.remove("txId");
            MDC.remove("cardId");
        }
    }

    public ScoringResponse scoringFallback(TransactionRequest request, Throwable throwable) {
        log.warn("Circuit breaker fallback triggered for cardId={}: {}",
                request.cardId(), throwable.getMessage());
        return new ScoringResponse(
                null,
                0,
                "APPROVE",
                List.of("ASYNC_REVIEW_REQUIRED"),
                0L
        );
    }
}
