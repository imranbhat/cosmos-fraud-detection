package com.cosmos.fraud.threeds.service;

import com.cosmos.fraud.threeds.client.ScoringClient;
import com.cosmos.fraud.threeds.dto.BrowserInfo;
import com.cosmos.fraud.threeds.dto.DeviceInfo;
import com.cosmos.fraud.threeds.dto.RiskAssessmentRequest;
import com.cosmos.fraud.threeds.dto.RiskAssessmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskBasedAuthServiceTest {

    @Mock
    private ScoringClient scoringClient;

    private RiskBasedAuthService service;

    @BeforeEach
    void setUp() {
        service = new RiskBasedAuthService(scoringClient);
    }

    // ---------------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------------

    private RiskAssessmentRequest buildRequest(String cardId) {
        DeviceInfo deviceInfo = new DeviceInfo("dev-001", "MOBILE", "iOS", "17.0", "iPhone15");
        BrowserInfo browserInfo = new BrowserInfo(
                "Mozilla/5.0", "text/html", "en-US",
                24, 1080, 1920, -480, false, true
        );
        return new RiskAssessmentRequest(
                cardId, "merchant-42", new BigDecimal("149.99"),
                "USD", deviceInfo, browserInfo, "2.2.0"
        );
    }

    private void mockScore(int score) {
        when(scoringClient.score(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new ScoringClient.ScoringResponse(score, "TEST"));
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Low-risk score (≤300) → frictionless Y, no challenge mandated")
    void lowRiskScore_returnsFrameless() {
        mockScore(150);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-low"));

        assertThat(response.transStatus()).isEqualTo("Y");
        assertThat(response.acsChallengeMandated()).isFalse();
        assertThat(response.riskScore()).isEqualTo(150);
        assertThat(response.threeDSServerTransID()).isNotBlank();
        assertThat(response.dsTransID()).isNotBlank();
        assertThat(response.messageVersion()).isEqualTo("2.2.0");
    }

    @Test
    @DisplayName("Boundary low-risk score (300) → frictionless Y")
    void boundaryLowRiskScore_returnsFrameless() {
        mockScore(300);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-boundary-low"));

        assertThat(response.transStatus()).isEqualTo("Y");
        assertThat(response.acsChallengeMandated()).isFalse();
    }

    @Test
    @DisplayName("Medium-risk score (301–700) → challenge C, ACS challenge mandated")
    void mediumRiskScore_returnsChallengeRequired() {
        mockScore(500);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-medium"));

        assertThat(response.transStatus()).isEqualTo("C");
        assertThat(response.acsChallengeMandated()).isTrue();
        assertThat(response.riskScore()).isEqualTo(500);
    }

    @Test
    @DisplayName("Boundary medium-risk score (301) → challenge C")
    void boundaryMediumRiskScore_returnsChallengeRequired() {
        mockScore(301);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-boundary-medium"));

        assertThat(response.transStatus()).isEqualTo("C");
        assertThat(response.acsChallengeMandated()).isTrue();
    }

    @Test
    @DisplayName("High-risk score (>700) → reject R, no challenge mandated")
    void highRiskScore_returnsReject() {
        mockScore(800);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-high"));

        assertThat(response.transStatus()).isEqualTo("R");
        assertThat(response.acsChallengeMandated()).isFalse();
        assertThat(response.riskScore()).isEqualTo(800);
    }

    @Test
    @DisplayName("Boundary high-risk score (701) → reject R")
    void boundaryHighRiskScore_returnsReject() {
        mockScore(701);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-boundary-high"));

        assertThat(response.transStatus()).isEqualTo("R");
        assertThat(response.acsChallengeMandated()).isFalse();
    }

    @Test
    @DisplayName("Fallback score (850) from circuit breaker → reject R")
    void fallbackScore_returnsReject() {
        mockScore(ScoringClient.FALLBACK_HIGH_RISK_SCORE);

        RiskAssessmentResponse response = service.assessRisk(buildRequest("card-fallback"));

        assertThat(response.transStatus()).isEqualTo("R");
        assertThat(response.acsChallengeMandated()).isFalse();
    }

    @Test
    @DisplayName("Each invocation generates unique transaction IDs")
    void uniqueTransactionIds() {
        mockScore(100);

        RiskAssessmentResponse r1 = service.assessRisk(buildRequest("card-a"));
        RiskAssessmentResponse r2 = service.assessRisk(buildRequest("card-b"));

        assertThat(r1.threeDSServerTransID()).isNotEqualTo(r2.threeDSServerTransID());
        assertThat(r1.dsTransID()).isNotEqualTo(r2.dsTransID());
    }

    @Test
    @DisplayName("Default messageVersion is used when not supplied in request")
    void defaultMessageVersion() {
        when(scoringClient.score(anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(new ScoringClient.ScoringResponse(100, "LOW"));

        // Build a request with an explicit null-like version (compact constructor replaces blank)
        RiskAssessmentRequest request = new RiskAssessmentRequest(
                "card-version", "merchant-1", new BigDecimal("10.00"),
                "EUR", null, null, null
        );

        RiskAssessmentResponse response = service.assessRisk(request);

        assertThat(response.messageVersion()).isEqualTo("2.2.0");
    }
}
