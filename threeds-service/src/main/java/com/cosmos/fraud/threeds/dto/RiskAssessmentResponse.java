package com.cosmos.fraud.threeds.dto;

/**
 * Response from 3DS risk assessment containing transaction status and authentication details.
 * <p>
 * transStatus values:
 * <ul>
 *   <li>Y – Authentication successful (frictionless)</li>
 *   <li>C – Challenge required</li>
 *   <li>R – Authentication rejected</li>
 *   <li>N – Authentication failed</li>
 * </ul>
 */
public record RiskAssessmentResponse(
        String threeDSServerTransID,
        String transStatus,
        boolean acsChallengeMandated,
        String dsTransID,
        String messageVersion,
        int riskScore
) {
}
