package com.cosmos.fraud.threeds.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for initiating a 3DS risk assessment.
 */
public record RiskAssessmentRequest(

        @NotBlank(message = "cardId is required")
        String cardId,

        @NotBlank(message = "merchantId is required")
        String merchantId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @Valid
        DeviceInfo deviceInfo,

        @Valid
        BrowserInfo browserInfo,

        String messageVersion
) {
    public RiskAssessmentRequest {
        if (messageVersion == null || messageVersion.isBlank()) {
            messageVersion = "2.2.0";
        }
    }
}
