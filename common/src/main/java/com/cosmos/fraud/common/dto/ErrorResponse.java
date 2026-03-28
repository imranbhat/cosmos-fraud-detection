package com.cosmos.fraud.common.dto;

import java.time.Instant;

public record ErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String correlationId
) {
}
