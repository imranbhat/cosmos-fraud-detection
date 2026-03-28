package com.cosmos.fraud.ingestion.controller;

import com.cosmos.fraud.common.dto.ScoringResponse;
import com.cosmos.fraud.common.dto.TransactionRequest;
import com.cosmos.fraud.ingestion.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    private static final String SCORE_ENDPOINT = "/v1/transactions/score";

    @Test
    @DisplayName("Valid transaction request returns 200 with ScoringResponse")
    void scoreTransaction_validRequest_returns200WithScoringResponse() throws Exception {
        TransactionRequest request = buildValidRequest();
        ScoringResponse scoringResponse = new ScoringResponse(
                "txId-123",
                25,
                "APPROVE",
                List.of("RULE_VELOCITY"),
                12L
        );

        when(transactionService.processTransaction(any(TransactionRequest.class)))
                .thenReturn(scoringResponse);

        mockMvc.perform(post(SCORE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txId", is("txId-123")))
                .andExpect(jsonPath("$.riskScore", is(25)))
                .andExpect(jsonPath("$.decision", is("APPROVE")))
                .andExpect(jsonPath("$.appliedRules[0]", is("RULE_VELOCITY")))
                .andExpect(jsonPath("$.latencyMs", is(12)));
    }

    @Test
    @DisplayName("Transaction request missing cardId returns 400")
    void scoreTransaction_missingCardId_returns400() throws Exception {
        TransactionRequest request = new TransactionRequest(
                null,              // cardId - missing
                "merchant-001",
                new BigDecimal("99.99"),
                "USD",
                "5411",
                "POS",
                40.7128,
                -74.0060,
                "US",
                null
        );

        mockMvc.perform(post(SCORE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validation errors return ErrorResponse with errorCode VALIDATION_ERROR")
    void scoreTransaction_validationErrors_returnsErrorResponse() throws Exception {
        TransactionRequest request = new TransactionRequest(
                "",                // cardId - blank
                "",                // merchantId - blank
                new BigDecimal("-10.00"), // amount - negative
                "INVALID_CURRENCY",       // currency - wrong length
                "",                       // mcc - blank
                "",                       // channel - blank
                null,
                null,
                "ZZZ",                    // country - wrong length
                null
        );

        mockMvc.perform(post(SCORE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Transaction request with blank amount field returns 400")
    void scoreTransaction_nullAmount_returns400() throws Exception {
        TransactionRequest request = new TransactionRequest(
                "card-001",
                "merchant-001",
                null,              // amount - null
                "USD",
                "5411",
                "POS",
                40.7128,
                -74.0060,
                "US",
                null
        );

        mockMvc.perform(post(SCORE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", containsString("amount must not be null")));
    }

    private TransactionRequest buildValidRequest() {
        return new TransactionRequest(
                "card-001",
                "merchant-001",
                new BigDecimal("150.00"),
                "USD",
                "5411",
                "POS",
                40.7128,
                -74.0060,
                "US",
                "fp-abc123"
        );
    }
}
