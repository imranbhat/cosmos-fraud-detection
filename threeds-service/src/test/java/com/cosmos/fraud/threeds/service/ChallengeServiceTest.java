package com.cosmos.fraud.threeds.service;

import com.cosmos.fraud.threeds.dto.ChallengeResultRequest;
import com.cosmos.fraud.threeds.dto.ChallengeResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChallengeServiceTest {

    private ChallengeService challengeService;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private String registerSession(int riskScore) {
        String transID = UUID.randomUUID().toString();
        challengeService.registerChallenge(transID, riskScore);
        return transID;
    }

    private ChallengeResultRequest successRequest(String transID) {
        return new ChallengeResultRequest(transID, "Y", "Y", "authValue-abc123");
    }

    private ChallengeResultRequest failedRequest(String transID) {
        return new ChallengeResultRequest(transID, "N", "Y", null);
    }

    // ---------------------------------------------------------------------------
    // Register + successful challenge
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Registered session + successful challenge → transStatus Y with ECI")
    void registerAndSuccessfulChallenge() {
        String transID = registerSession(400);

        ChallengeResultResponse response = challengeService.processChallenge(successRequest(transID));

        assertThat(response.threeDSServerTransID()).isEqualTo(transID);
        assertThat(response.transStatus()).isEqualTo("Y");
        assertThat(response.authenticationValue()).isEqualTo("authValue-abc123");
        assertThat(response.eci()).isIn("05", "02");
    }

    @Test
    @DisplayName("Successful challenge with even riskScore → ECI 05 (Visa)")
    void successfulChallenge_evenScore_visaEci() {
        String transID = registerSession(400); // even

        ChallengeResultResponse response = challengeService.processChallenge(successRequest(transID));

        assertThat(response.eci()).isEqualTo("05");
    }

    @Test
    @DisplayName("Successful challenge with odd riskScore → ECI 02 (Mastercard)")
    void successfulChallenge_oddScore_mastercardEci() {
        String transID = registerSession(401); // odd

        ChallengeResultResponse response = challengeService.processChallenge(successRequest(transID));

        assertThat(response.eci()).isEqualTo("02");
    }

    // ---------------------------------------------------------------------------
    // Register + failed challenge
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Registered session + failed challenge → transStatus N with ECI 07")
    void registerAndFailedChallenge() {
        String transID = registerSession(600);

        ChallengeResultResponse response = challengeService.processChallenge(failedRequest(transID));

        assertThat(response.threeDSServerTransID()).isEqualTo(transID);
        assertThat(response.transStatus()).isEqualTo("N");
        assertThat(response.eci()).isEqualTo("07");
        assertThat(response.authenticationValue()).isNull();
    }

    // ---------------------------------------------------------------------------
    // Unknown session
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Unknown session ID → throws IllegalArgumentException")
    void unknownSession_throwsIllegalArgumentException() {
        String unknownTransID = UUID.randomUUID().toString();
        ChallengeResultRequest request = successRequest(unknownTransID);

        assertThatThrownBy(() -> challengeService.processChallenge(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(unknownTransID);
    }

    // ---------------------------------------------------------------------------
    // Session tracking
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Active sessions count increases on register, decreases after processing")
    void activeSessionCount() {
        assertThat(challengeService.activeSessions()).isZero();

        String t1 = registerSession(500);
        String t2 = registerSession(550);
        assertThat(challengeService.activeSessions()).isEqualTo(2);

        challengeService.processChallenge(successRequest(t1));
        assertThat(challengeService.activeSessions()).isEqualTo(1);

        challengeService.processChallenge(failedRequest(t2));
        assertThat(challengeService.activeSessions()).isZero();
    }
}
