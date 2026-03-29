package com.cosmos.fraud.audit.mapper;

import com.cosmos.fraud.audit.model.AnalyticsRecord;
import com.cosmos.fraud.audit.model.AuditRecord;
import com.cosmos.fraud.avro.Decision;
import com.cosmos.fraud.avro.FraudDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuditMapper}.
 * No Spring context needed — pure logic tests.
 */
class AuditMapperTest {

    private static final long NOW_MILLIS = Instant.parse("2024-06-01T12:00:00Z").toEpochMilli();

    private FraudDecision buildDecision() {
        return FraudDecision.newBuilder()
                .setTxId("tx-001")
                .setCardId("card-999")
                .setRiskScore(82)
                .setDecision(Decision.DECLINE)
                .setAppliedRules(List.of("HIGH_AMOUNT", "NEW_MERCHANT"))
                .setModelScores(Map.of("xgboost", 0.91, "iso_forest", 0.78))
                .setLatencyMs(45L)
                .setTimestamp(NOW_MILLIS)
                .build();
    }

    // -------------------------------------------------------------------------
    // fromFraudDecision
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fromFraudDecision maps all Avro fields to AuditRecord correctly")
    void fromFraudDecision_mapsAllFields() {
        FraudDecision decision = buildDecision();
        String rawEvent = "{\"txId\":\"tx-001\"}";

        AuditRecord record = AuditMapper.fromFraudDecision(decision, rawEvent);

        assertThat(record.txId()).isEqualTo("tx-001");
        assertThat(record.cardId()).isEqualTo("card-999");
        assertThat(record.riskScore()).isEqualTo(82);
        assertThat(record.decision()).isEqualTo("DECLINE");
        assertThat(record.appliedRules()).containsExactlyInAnyOrder("HIGH_AMOUNT", "NEW_MERCHANT");
        assertThat(record.modelScores()).containsEntry("xgboost", 0.91);
        assertThat(record.modelScores()).containsEntry("iso_forest", 0.78);
        assertThat(record.latencyMs()).isEqualTo(45L);
        assertThat(record.timestamp()).isEqualTo(Instant.ofEpochMilli(NOW_MILLIS));
        assertThat(record.rawEvent()).isEqualTo(rawEvent);
    }

    @Test
    @DisplayName("fromFraudDecision uses empty lists/maps when Avro fields are null")
    void fromFraudDecision_handlesNullCollections() {
        FraudDecision decision = FraudDecision.newBuilder()
                .setTxId("tx-002")
                .setCardId("card-000")
                .setRiskScore(0)
                .setDecision(Decision.APPROVE)
                .setAppliedRules(List.of())
                .setModelScores(Map.of())
                .setLatencyMs(10L)
                .setTimestamp(NOW_MILLIS)
                .build();

        AuditRecord record = AuditMapper.fromFraudDecision(decision, "{}");

        assertThat(record.appliedRules()).isEmpty();
        assertThat(record.modelScores()).isEmpty();
    }

    @Test
    @DisplayName("fromFraudDecision defaults merchantId to empty string and amount to ZERO")
    void fromFraudDecision_defaultsMissingFields() {
        AuditRecord record = AuditMapper.fromFraudDecision(buildDecision(), "{}");

        assertThat(record.merchantId()).isEmpty();
        assertThat(record.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // toAnalyticsRecord
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("toAnalyticsRecord projects AuditRecord fields to AnalyticsRecord correctly")
    void toAnalyticsRecord_projectsFields() {
        AuditRecord audit = AuditMapper.fromFraudDecision(buildDecision(), "{}");

        AnalyticsRecord analytics = AuditMapper.toAnalyticsRecord(audit);

        assertThat(analytics.txId()).isEqualTo(audit.txId());
        assertThat(analytics.cardId()).isEqualTo(audit.cardId());
        assertThat(analytics.merchantId()).isEqualTo(audit.merchantId());
        assertThat(analytics.amount()).isEqualTo(audit.amount().doubleValue());
        assertThat(analytics.riskScore()).isEqualTo(audit.riskScore());
        assertThat(analytics.decision()).isEqualTo(audit.decision());
        assertThat(analytics.latencyMs()).isEqualTo(audit.latencyMs());
        assertThat(analytics.timestamp()).isEqualTo(audit.timestamp());
    }

    @Test
    @DisplayName("toAnalyticsRecord defaults country/channel/mcc to empty strings")
    void toAnalyticsRecord_defaultsEnrichmentFields() {
        AnalyticsRecord analytics = AuditMapper.toAnalyticsRecord(
                AuditMapper.fromFraudDecision(buildDecision(), "{}"));

        assertThat(analytics.country()).isEmpty();
        assertThat(analytics.channel()).isEmpty();
        assertThat(analytics.mcc()).isEmpty();
    }

    @Test
    @DisplayName("toAnalyticsRecord round-trips amount from BigDecimal to double without precision loss for small values")
    void toAnalyticsRecord_amountRoundTrip() {
        // A non-zero amount injected via a manually constructed AuditRecord
        AuditRecord audit = new AuditRecord(
                "tx-003", "card-001", "merch-01",
                new BigDecimal("123.45"), 55, "APPROVE",
                List.of(), Map.of(), 20L,
                Instant.now(), "{}"
        );

        AnalyticsRecord analytics = AuditMapper.toAnalyticsRecord(audit);

        assertThat(analytics.amount()).isEqualTo(123.45);
    }
}
