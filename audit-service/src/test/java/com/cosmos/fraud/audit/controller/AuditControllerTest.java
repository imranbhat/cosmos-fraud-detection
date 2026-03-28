package com.cosmos.fraud.audit.controller;

import com.cosmos.fraud.audit.model.AuditRecord;
import com.cosmos.fraud.audit.repository.AuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuditController}.
 *
 * <p>Only the web layer is loaded; all dependencies are mocked via {@link MockBean}.
 */
@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditRepository auditRepository;

    @MockBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuditRecord sampleRecord(String txId) {
        return new AuditRecord(
                txId,
                "card-42",
                "merch-77",
                new BigDecimal("250.00"),
                75,
                "DECLINE",
                List.of("HIGH_VELOCITY"),
                Map.of("xgboost", 0.88),
                38L,
                Instant.parse("2024-03-15T10:00:00Z"),
                "{\"txId\":\"" + txId + "\"}"
        );
    }

    // -------------------------------------------------------------------------
    // GET /v1/transactions/{txId}/audit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/transactions/{txId}/audit returns 200 with AuditRecord when found")
    void getAuditRecord_returns200WhenFound() throws Exception {
        AuditRecord record = sampleRecord("tx-100");
        when(auditRepository.findByTxId("tx-100")).thenReturn(Optional.of(record));

        mockMvc.perform(get("/v1/transactions/tx-100/audit")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.txId", is("tx-100")))
                .andExpect(jsonPath("$.cardId", is("card-42")))
                .andExpect(jsonPath("$.decision", is("DECLINE")))
                .andExpect(jsonPath("$.riskScore", is(75)))
                .andExpect(jsonPath("$.latencyMs", is(38)));
    }

    @Test
    @DisplayName("GET /v1/transactions/{txId}/audit returns 404 for unknown txId")
    void getAuditRecord_returns404WhenNotFound() throws Exception {
        when(auditRepository.findByTxId("tx-unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/transactions/tx-unknown/audit")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /v1/transactions/{txId}/audit returns full appliedRules list")
    void getAuditRecord_returnsAppliedRules() throws Exception {
        AuditRecord record = new AuditRecord(
                "tx-200", "card-1", "merch-1",
                BigDecimal.TEN, 90, "DECLINE",
                List.of("RULE_A", "RULE_B"),
                Map.of(),
                20L, Instant.now(), "{}"
        );
        when(auditRepository.findByTxId("tx-200")).thenReturn(Optional.of(record));

        mockMvc.perform(get("/v1/transactions/tx-200/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedRules[0]", is("RULE_A")))
                .andExpect(jsonPath("$.appliedRules[1]", is("RULE_B")));
    }

    // -------------------------------------------------------------------------
    // POST /v1/audit/replay
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/audit/replay returns 202 Accepted with scanned/published counts")
    void replay_returns202WithStats() throws Exception {
        List<AuditRecord> records = List.of(sampleRecord("tx-r1"), sampleRecord("tx-r2"));
        when(auditRepository.findByCardId(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(records);

        //noinspection unchecked
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        mockMvc.perform(post("/v1/audit/replay")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-31T23:59:59Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scanned", is(2)))
                .andExpect(jsonPath("$.published", is(2)))
                .andExpect(jsonPath("$.from", is("2024-01-01T00:00:00Z")))
                .andExpect(jsonPath("$.to", is("2024-01-31T23:59:59Z")));
    }

    @Test
    @DisplayName("POST /v1/audit/replay with no matching records returns 202 with zeros")
    void replay_returnsZeroCountsWhenNoRecords() throws Exception {
        when(auditRepository.findByCardId(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        mockMvc.perform(post("/v1/audit/replay")
                        .param("from", "2024-06-01T00:00:00Z")
                        .param("to", "2024-06-01T01:00:00Z"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scanned", is(0)))
                .andExpect(jsonPath("$.published", is(0)));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /v1/audit/replay publishes each record to Kafka once")
    void replay_publishesEachRecordOnce() throws Exception {
        List<AuditRecord> records = List.of(
                sampleRecord("tx-a"), sampleRecord("tx-b"), sampleRecord("tx-c"));
        when(auditRepository.findByCardId(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(records);

        //noinspection unchecked
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        mockMvc.perform(post("/v1/audit/replay")
                        .param("from", "2024-02-01T00:00:00Z")
                        .param("to", "2024-02-28T23:59:59Z"))
                .andExpect(status().isAccepted());

        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any());
    }
}
