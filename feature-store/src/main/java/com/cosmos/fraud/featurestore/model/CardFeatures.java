package com.cosmos.fraud.featurestore.model;

/**
 * Immutable snapshot of computed features for a card at a point in time.
 * All counts and aggregates are derived from Redis-backed sliding windows.
 */
public record CardFeatures(
        String cardId,
        int txCountOneHour,
        int txCountSixHours,
        int txCountTwentyFourHours,
        double avgAmountSevenDays,
        int distinctMerchantsTwentyFourHours,
        String lastCountry,
        boolean countryChanged,
        long timeSinceLastTxMs,
        String deviceHash,
        double velocityScore
) {

    /** Default/empty features returned when no data exists for a card. */
    public static CardFeatures defaultFor(String cardId) {
        return new CardFeatures(cardId, 0, 0, 0, 0.0, 0, null, false, -1L, null, 0.0);
    }
}
