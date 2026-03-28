package com.cosmos.fraud.threeds.dto;

/**
 * Response after processing a 3DS challenge result.
 * <p>
 * ECI (Electronic Commerce Indicator) values (simplified):
 * <ul>
 *   <li>05 – Visa: Fully authenticated</li>
 *   <li>02 – Mastercard: Fully authenticated</li>
 * </ul>
 */
public record ChallengeResultResponse(
        String threeDSServerTransID,
        String transStatus,
        String authenticationValue,
        String eci
) {
}
