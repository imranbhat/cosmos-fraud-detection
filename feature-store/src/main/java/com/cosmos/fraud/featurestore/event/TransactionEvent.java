package com.cosmos.fraud.featurestore.event;

import java.math.BigDecimal;

/**
 * Lightweight event model representing the fields required by the feature-store
 * to update sliding-window aggregates for a card.
 */
public record TransactionEvent(
        String cardId,
        String merchantId,
        BigDecimal amount,
        String country,
        String deviceHash,
        long timestampMs
) {
}
