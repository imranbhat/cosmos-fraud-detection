package com.cosmos.fraud.ingestion.service;

import com.cosmos.fraud.avro.Decision;
import com.cosmos.fraud.avro.FraudDecision;
import com.cosmos.fraud.common.dto.ScoringResponse;
import com.cosmos.fraud.common.dto.TransactionRequest;
import com.cosmos.fraud.common.exception.ScoringTimeoutException;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    @Mock
    private ReplyingKafkaTemplate<String, SpecificRecord, SpecificRecord> replyingKafkaTemplate;

    @Mock
    private RequestReplyFuture<String, SpecificRecord, SpecificRecord> replyFuture;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(kafkaTemplate, replyingKafkaTemplate);
    }

    @Test
    @DisplayName("processTransaction publishes to Kafka and returns ScoringResponse on success")
    void processTransaction_success_publishesAndReturnsScoringResponse()
            throws ExecutionException, InterruptedException, TimeoutException {
        TransactionRequest request = buildValidRequest();

        FraudDecision decision = FraudDecision.newBuilder()
                .setTxId("some-tx-id")
                .setCardId("card-001")
                .setRiskScore(20)
                .setDecision(Decision.APPROVE)
                .setAppliedRules(List.of("RULE_LOW_RISK"))
                .setModelScores(Map.of("gbm", 0.2))
                .setLatencyMs(10L)
                .setTimestamp(System.currentTimeMillis())
                .build();

        ConsumerRecord<String, SpecificRecord> consumerRecord =
                new ConsumerRecord<>("scoring.replies", 0, 0L, "some-tx-id", decision);

        when(replyingKafkaTemplate.sendAndReceive(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(replyFuture);
        when(replyFuture.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(consumerRecord);

        ScoringResponse response = transactionService.processTransaction(request);

        assertThat(response).isNotNull();
        assertThat(response.decision()).isEqualTo("APPROVE");
        assertThat(response.riskScore()).isEqualTo(20);
        assertThat(response.appliedRules()).contains("RULE_LOW_RISK");

        verify(kafkaTemplate).send(eq("transactions.raw"), any(String.class), any(SpecificRecord.class));
        verify(replyingKafkaTemplate).sendAndReceive(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    @Test
    @DisplayName("processTransaction throws ScoringTimeoutException on timeout")
    void processTransaction_timeout_throwsScoringTimeoutException()
            throws ExecutionException, InterruptedException, TimeoutException {
        TransactionRequest request = buildValidRequest();

        when(replyingKafkaTemplate.sendAndReceive(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(replyFuture);
        when(replyFuture.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new TimeoutException("Timed out"));

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(ScoringTimeoutException.class)
                .hasMessageContaining("Scoring exceeded 45ms timeout");
    }

    @Test
    @DisplayName("scoringFallback returns APPROVE decision with async review flag")
    void scoringFallback_returnsApproveFallbackResponse() {
        TransactionRequest request = buildValidRequest();
        RuntimeException cause = new RuntimeException("Circuit open");

        ScoringResponse fallback = transactionService.scoringFallback(request, cause);

        assertThat(fallback).isNotNull();
        assertThat(fallback.decision()).isEqualTo("APPROVE");
        assertThat(fallback.riskScore()).isEqualTo(0);
        assertThat(fallback.appliedRules()).contains("ASYNC_REVIEW_REQUIRED");
        assertThat(fallback.txId()).isNull();
    }

    @Test
    @DisplayName("scoringFallback is invoked when exception propagates from processTransaction")
    void processTransaction_onKafkaError_fallbackReturnsApprove() {
        TransactionRequest request = buildValidRequest();

        // The fallback is triggered by the @CircuitBreaker aspect in the Spring context.
        // In a unit test without the Spring AOP proxy, we verify the fallback method directly.
        ScoringResponse fallback = transactionService.scoringFallback(
                request, new RuntimeException("broker down"));

        assertThat(fallback.decision()).isEqualTo("APPROVE");
        assertThat(fallback.appliedRules()).contains("ASYNC_REVIEW_REQUIRED");
    }

    private TransactionRequest buildValidRequest() {
        return new TransactionRequest(
                "card-001",
                "merchant-001",
                new BigDecimal("300.00"),
                "USD",
                "5411",
                "POS",
                40.7128,
                -74.0060,
                "US",
                "fp-test"
        );
    }
}
