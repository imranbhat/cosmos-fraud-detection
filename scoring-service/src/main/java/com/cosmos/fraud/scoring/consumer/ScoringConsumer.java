package com.cosmos.fraud.scoring.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.avro.FraudDecision;
import com.cosmos.fraud.common.kafka.KafkaTopics;
import com.cosmos.fraud.scoring.service.ScoringService;

@Component
public class ScoringConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScoringConsumer.class);
    private static final String SCORING_REPLIES_TOPIC = "scoring.replies";

    private final ScoringService scoringService;
    private final KafkaTemplate<String, FraudDecision> kafkaTemplate;

    public ScoringConsumer(ScoringService scoringService,
                           KafkaTemplate<String, FraudDecision> kafkaTemplate) {
        this.scoringService = scoringService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = KafkaTopics.TRANSACTIONS_ENRICHED,
            groupId = "scoring-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEnrichedTransaction(
            ConsumerRecord<String, EnrichedTransaction> record,
            @Header(value = KafkaHeaders.REPLY_TOPIC, required = false) byte[] replyTopicBytes) {

        EnrichedTransaction tx = record.value();
        String txId = tx.getTxId() != null ? tx.getTxId().toString() : "unknown";

        log.debug("Received enriched transaction: txId={}, partition={}, offset={}",
                txId, record.partition(), record.offset());

        try {
            FraudDecision decision = scoringService.score(tx);

            // Publish to the main fraud decisions topic
            kafkaTemplate.send(KafkaTopics.FRAUD_DECISIONS, txId, decision)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish FraudDecision for txId={}: {}", txId, ex.getMessage(), ex);
                        } else {
                            log.debug("Published FraudDecision for txId={}: decision={}, score={}",
                                    txId, decision.getDecision(), decision.getRiskScore());
                        }
                    });

            // If a reply topic header is present (sync request/reply pattern), send there too
            if (replyTopicBytes != null) {
                String replyTopic = new String(replyTopicBytes);
                kafkaTemplate.send(replyTopic, txId, decision)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to send reply for txId={} to {}: {}", txId, replyTopic, ex.getMessage());
                            }
                        });
            }

        } catch (Exception e) {
            log.error("Unexpected error scoring txId={}: {}", txId, e.getMessage(), e);
        }
    }
}
