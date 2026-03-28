package com.cosmos.fraud.threeds.service;

import com.cosmos.fraud.threeds.client.ScoringClient;
import com.cosmos.fraud.threeds.dto.RiskAssessmentRequest;
import com.cosmos.fraud.threeds.dto.RiskAssessmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Risk-Based Authentication (RBA) service implementing the 3DS 2.x frictionless
 * and challenge-flow decision logic.
 *
 * <p>Score bands (mirrored from scoring-service thresholds):
 * <ul>
 *   <li>0–300   → Y (frictionless approve)</li>
 *   <li>301–700 → C (challenge required)</li>
 *   <li>701–1000 → R (reject)</li>
 * </ul>
 */
@Service
public class RiskBasedAuthService {

    private static final Logger log = LoggerFactory.getLogger(RiskBasedAuthService.class);

    static final int LOW_RISK_THRESHOLD = 300;
    static final int HIGH_RISK_THRESHOLD = 700;

    static final String STATUS_FRICTIONLESS = "Y";
    static final String STATUS_CHALLENGE    = "C";
    static final String STATUS_REJECT       = "R";

    private final ScoringClient scoringClient;

    public RiskBasedAuthService(ScoringClient scoringClient) {
        this.scoringClient = scoringClient;
    }

    /**
     * Assesses the fraud risk of the transaction and returns a 3DS authentication decision.
     *
     * @param request the risk assessment request
     * @return a {@link RiskAssessmentResponse} with transaction status and risk metadata
     */
    public RiskAssessmentResponse assessRisk(RiskAssessmentRequest request) {
        String threeDSServerTransID = UUID.randomUUID().toString();
        String dsTransID            = UUID.randomUUID().toString();

        String deviceId = request.deviceInfo() != null ? request.deviceInfo().deviceId() : null;

        ScoringClient.ScoringResponse scoringResponse = scoringClient.score(
                request.cardId(),
                request.merchantId(),
                request.amount(),
                request.currency(),
                deviceId
        );

        int riskScore = scoringResponse.riskScore();
        String transStatus;
        boolean acsChallengeMandated;

        if (riskScore <= LOW_RISK_THRESHOLD) {
            transStatus          = STATUS_FRICTIONLESS;
            acsChallengeMandated = false;
            log.info("transID={} riskScore={} → frictionless (Y)", threeDSServerTransID, riskScore);

        } else if (riskScore <= HIGH_RISK_THRESHOLD) {
            transStatus          = STATUS_CHALLENGE;
            acsChallengeMandated = true;
            log.info("transID={} riskScore={} → challenge required (C)", threeDSServerTransID, riskScore);

        } else {
            transStatus          = STATUS_REJECT;
            acsChallengeMandated = false;
            log.info("transID={} riskScore={} → rejected (R)", threeDSServerTransID, riskScore);
        }

        return new RiskAssessmentResponse(
                threeDSServerTransID,
                transStatus,
                acsChallengeMandated,
                dsTransID,
                request.messageVersion(),
                riskScore
        );
    }
}
