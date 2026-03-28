package com.cosmos.fraud.threeds.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for submitting the result of a 3DS challenge.
 */
public record ChallengeResultRequest(

        @NotBlank(message = "threeDSServerTransID is required")
        String threeDSServerTransID,

        @NotBlank(message = "transStatus is required")
        String transStatus,

        String challengeCompletionInd,

        String authenticationValue
) {
}
