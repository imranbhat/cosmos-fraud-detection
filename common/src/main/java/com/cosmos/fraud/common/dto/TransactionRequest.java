package com.cosmos.fraud.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank(message = "cardId must not be blank")
        String cardId,

        @NotBlank(message = "merchantId must not be blank")
        String merchantId,

        @NotNull(message = "amount must not be null")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "currency must not be blank")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        String currency,

        @NotBlank(message = "mcc must not be blank")
        String mcc,

        @NotBlank(message = "channel must not be blank")
        String channel,

        Double latitude,

        Double longitude,

        @NotBlank(message = "country must not be blank")
        @Size(min = 2, max = 2, message = "country must be a 2-letter ISO code")
        String country,

        String deviceFingerprint
) {
}
