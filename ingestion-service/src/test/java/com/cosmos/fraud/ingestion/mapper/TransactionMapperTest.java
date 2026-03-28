package com.cosmos.fraud.ingestion.mapper;

import com.cosmos.fraud.avro.Decision;
import com.cosmos.fraud.avro.FraudDecision;
import com.cosmos.fraud.avro.TransactionEvent;
import com.cosmos.fraud.common.dto.ScoringResponse;
import com.cosmos.fraud.common.dto.TransactionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperTest {

    private static final String TX_ID = "01HX123ABC";

    @Test
    @DisplayName("toAvroEvent maps all fields correctly from TransactionRequest")
    void toAvroEvent_mapsAllFieldsCorrectly() {
        TransactionRequest request = new TransactionRequest(
                "card-001",
                "merchant-001",
                new BigDecimal("250.75"),
                "EUR",
                "5411",
                "ECOM",
                48.8566,
                2.3522,
                "FR",
                "fp-xyz789"
        );

        TransactionEvent event = TransactionMapper.toAvroEvent(request, TX_ID);

        assertThat(event.getTxId().toString()).isEqualTo(TX_ID);
        assertThat(event.getCardId().toString()).isEqualTo("card-001");
        assertThat(event.getMerchantId().toString()).isEqualTo("merchant-001");
        assertThat(event.getAmount()).isEqualTo(250.75);
        assertThat(event.getCurrency().toString()).isEqualTo("EUR");
        assertThat(event.getMcc().toString()).isEqualTo("5411");
        assertThat(event.getChannel().name()).isEqualTo("ECOM");
        assertThat(event.getLocation().getLat()).isEqualTo(48.8566);
        assertThat(event.getLocation().getLon()).isEqualTo(2.3522);
        assertThat(event.getLocation().getCountry().toString()).isEqualTo("FR");
        assertThat(event.getDeviceFingerprint().toString()).isEqualTo("fp-xyz789");
        assertThat(event.getTimestamp()).isPositive();
    }

    @Test
    @DisplayName("toAvroEvent handles null latitude and longitude gracefully")
    void toAvroEvent_nullLatLon_defaultsToZero() {
        TransactionRequest request = new TransactionRequest(
                "card-001",
                "merchant-001",
                new BigDecimal("50.00"),
                "USD",
                "5999",
                "ATM",
                null,
                null,
                "US",
                null
        );

        TransactionEvent event = TransactionMapper.toAvroEvent(request, TX_ID);

        assertThat(event.getLocation().getLat()).isEqualTo(0.0);
        assertThat(event.getLocation().getLon()).isEqualTo(0.0);
        assertThat(event.getDeviceFingerprint()).isNull();
    }

    @Test
    @DisplayName("toAvroEvent defaults unknown channel to ECOM")
    void toAvroEvent_unknownChannel_defaultsToEcom() {
        TransactionRequest request = new TransactionRequest(
                "card-001",
                "merchant-001",
                new BigDecimal("10.00"),
                "GBP",
                "5999",
                "MOBILE",  // unsupported channel
                51.5074,
                -0.1278,
                "GB",
                null
        );

        TransactionEvent event = TransactionMapper.toAvroEvent(request, TX_ID);

        assertThat(event.getChannel().name()).isEqualTo("ECOM");
    }

    @Test
    @DisplayName("toScoringResponse maps decision fields correctly from FraudDecision")
    void toScoringResponse_mapsDecisionFieldsCorrectly() {
        FraudDecision decision = FraudDecision.newBuilder()
                .setTxId(TX_ID)
                .setCardId("card-001")
                .setRiskScore(75)
                .setDecision(Decision.DECLINE)
                .setAppliedRules(List.of("RULE_HIGH_RISK_COUNTRY", "RULE_VELOCITY_BREACH"))
                .setModelScores(Map.of("gbm", 0.82, "nn", 0.71))
                .setLatencyMs(18L)
                .setTimestamp(System.currentTimeMillis())
                .build();

        ScoringResponse response = TransactionMapper.toScoringResponse(decision);

        assertThat(response.txId()).isEqualTo(TX_ID);
        assertThat(response.riskScore()).isEqualTo(75);
        assertThat(response.decision()).isEqualTo("DECLINE");
        assertThat(response.appliedRules())
                .containsExactlyInAnyOrder("RULE_HIGH_RISK_COUNTRY", "RULE_VELOCITY_BREACH");
        assertThat(response.latencyMs()).isEqualTo(18L);
    }

    @Test
    @DisplayName("toScoringResponse handles empty appliedRules gracefully")
    void toScoringResponse_emptyAppliedRules_returnsEmptyList() {
        FraudDecision decision = FraudDecision.newBuilder()
                .setTxId(TX_ID)
                .setCardId("card-001")
                .setRiskScore(10)
                .setDecision(Decision.APPROVE)
                .setAppliedRules(List.of())
                .setModelScores(Map.of())
                .setLatencyMs(8L)
                .setTimestamp(System.currentTimeMillis())
                .build();

        ScoringResponse response = TransactionMapper.toScoringResponse(decision);

        assertThat(response.appliedRules()).isEmpty();
        assertThat(response.decision()).isEqualTo("APPROVE");
    }
}
