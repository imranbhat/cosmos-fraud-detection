package com.cosmos.fraud.threeds.controller;

import com.cosmos.fraud.threeds.dto.ChallengeResultRequest;
import com.cosmos.fraud.threeds.dto.ChallengeResultResponse;
import com.cosmos.fraud.threeds.dto.RiskAssessmentRequest;
import com.cosmos.fraud.threeds.dto.RiskAssessmentResponse;
import com.cosmos.fraud.threeds.service.ChallengeService;
import com.cosmos.fraud.threeds.service.RiskBasedAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ThreeDSController.class)
class ThreeDSControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RiskBasedAuthService riskBasedAuthService;

    @MockitoBean
    private ChallengeService challengeService;

    // ---------------------------------------------------------------------------
    // POST /v1/threeds/risk-assessment
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /risk-assessment with valid request → 200 OK with frictionless Y response")
    void riskAssessment_validRequest_returns200() throws Exception {
        String transID = UUID.randomUUID().toString();
        RiskAssessmentResponse mockResponse = new RiskAssessmentResponse(
                transID, "Y", false, UUID.randomUUID().toString(), "2.2.0", 150
        );
        when(riskBasedAuthService.assessRisk(any())).thenReturn(mockResponse);

        RiskAssessmentRequest request = new RiskAssessmentRequest(
                "card-001", "merchant-001", new BigDecimal("99.99"),
                "USD", null, null, null
        );

        mockMvc.perform(post("/v1/threeds/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threeDSServerTransID").value(transID))
                .andExpect(jsonPath("$.transStatus").value("Y"))
                .andExpect(jsonPath("$.acsChallengeMandated").value(false))
                .andExpect(jsonPath("$.riskScore").value(150))
                .andExpect(jsonPath("$.messageVersion").value("2.2.0"));
    }

    @Test
    @DisplayName("POST /risk-assessment with challenge response → registers challenge session")
    void riskAssessment_challengeResponse_registersChallengeSession() throws Exception {
        String transID = UUID.randomUUID().toString();
        RiskAssessmentResponse mockResponse = new RiskAssessmentResponse(
                transID, "C", true, UUID.randomUUID().toString(), "2.2.0", 500
        );
        when(riskBasedAuthService.assessRisk(any())).thenReturn(mockResponse);

        RiskAssessmentRequest request = new RiskAssessmentRequest(
                "card-002", "merchant-002", new BigDecimal("500.00"),
                "EUR", null, null, null
        );

        mockMvc.perform(post("/v1/threeds/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transStatus").value("C"))
                .andExpect(jsonPath("$.acsChallengeMandated").value(true));
    }

    @Test
    @DisplayName("POST /risk-assessment missing required fields → 400 Bad Request with validation details")
    void riskAssessment_missingCardId_returns400() throws Exception {
        // Missing cardId
        String invalidJson = """
                {
                  "merchantId": "merchant-001",
                  "amount": "49.99",
                  "currency": "USD"
                }
                """;

        mockMvc.perform(post("/v1/threeds/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("POST /risk-assessment with invalid currency length → 400 Bad Request")
    void riskAssessment_invalidCurrency_returns400() throws Exception {
        String invalidJson = """
                {
                  "cardId": "card-001",
                  "merchantId": "merchant-001",
                  "amount": "49.99",
                  "currency": "US"
                }
                """;

        mockMvc.perform(post("/v1/threeds/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }

    @Test
    @DisplayName("POST /risk-assessment with zero amount → 400 Bad Request")
    void riskAssessment_zeroAmount_returns400() throws Exception {
        String invalidJson = """
                {
                  "cardId": "card-001",
                  "merchantId": "merchant-001",
                  "amount": "0.00",
                  "currency": "USD"
                }
                """;

        mockMvc.perform(post("/v1/threeds/risk-assessment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }

    // ---------------------------------------------------------------------------
    // POST /v1/threeds/challenge-result
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("POST /challenge-result with valid request → 200 OK with authenticated Y response")
    void challengeResult_validRequest_returns200() throws Exception {
        String transID = UUID.randomUUID().toString();
        ChallengeResultResponse mockResponse = new ChallengeResultResponse(
                transID, "Y", "authValue-xyz", "05"
        );
        when(challengeService.processChallenge(any())).thenReturn(mockResponse);

        ChallengeResultRequest request = new ChallengeResultRequest(
                transID, "Y", "Y", "authValue-xyz"
        );

        mockMvc.perform(post("/v1/threeds/challenge-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threeDSServerTransID").value(transID))
                .andExpect(jsonPath("$.transStatus").value("Y"))
                .andExpect(jsonPath("$.authenticationValue").value("authValue-xyz"))
                .andExpect(jsonPath("$.eci").value("05"));
    }

    @Test
    @DisplayName("POST /challenge-result with failed challenge → 200 OK with status N")
    void challengeResult_failedChallenge_returns200() throws Exception {
        String transID = UUID.randomUUID().toString();
        ChallengeResultResponse mockResponse = new ChallengeResultResponse(
                transID, "N", null, "07"
        );
        when(challengeService.processChallenge(any())).thenReturn(mockResponse);

        ChallengeResultRequest request = new ChallengeResultRequest(
                transID, "N", "Y", null
        );

        mockMvc.perform(post("/v1/threeds/challenge-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transStatus").value("N"))
                .andExpect(jsonPath("$.eci").value("07"));
    }

    @Test
    @DisplayName("POST /challenge-result with unknown transID → 404 Not Found")
    void challengeResult_unknownSession_returns404() throws Exception {
        String unknownTransID = UUID.randomUUID().toString();
        doThrow(new IllegalArgumentException("Challenge session not found for transID: " + unknownTransID))
                .when(challengeService).processChallenge(any());

        ChallengeResultRequest request = new ChallengeResultRequest(
                unknownTransID, "Y", "Y", "authValue"
        );

        mockMvc.perform(post("/v1/threeds/challenge-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Challenge Session Not Found"));
    }

    @Test
    @DisplayName("POST /challenge-result missing threeDSServerTransID → 400 Bad Request")
    void challengeResult_missingTransID_returns400() throws Exception {
        String invalidJson = """
                {
                  "transStatus": "Y",
                  "challengeCompletionInd": "Y"
                }
                """;

        mockMvc.perform(post("/v1/threeds/challenge-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }

    @Test
    @DisplayName("POST /challenge-result missing transStatus → 400 Bad Request")
    void challengeResult_missingTransStatus_returns400() throws Exception {
        String invalidJson = """
                {
                  "threeDSServerTransID": "some-trans-id"
                }
                """;

        mockMvc.perform(post("/v1/threeds/challenge-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"));
    }
}
