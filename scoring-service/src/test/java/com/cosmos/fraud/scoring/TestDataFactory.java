package com.cosmos.fraud.scoring;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.cosmos.fraud.avro.Channel;
import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.avro.Location;

/**
 * Factory for constructing EnrichedTransaction Avro objects for use in tests.
 * All builders use sensible "low-risk" defaults that individual tests can override.
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /** Low-risk transaction: daytime, normal amount, low velocity, no geo change. */
    public static EnrichedTransaction lowRiskTransaction() {
        return builder()
                .txId("tx-low-risk-001")
                .amount(50.0)
                .avgAmountSevenDays(60.0)
                .txCountOneHour(2)
                .countryChanged(false)
                .timeSinceLastTxMs(7_200_000L) // 2 hours
                .mcc("5411")                   // Grocery store
                .deviceFingerprint("fp-abc123")
                .deviceHash("fp-abc123")       // Same device
                .timestampHour(10)             // 10 AM UTC
                .build();
    }

    /** High-risk transaction: all rules fire. */
    public static EnrichedTransaction highRiskTransaction() {
        return builder()
                .txId("tx-high-risk-001")
                .amount(1000.0)
                .avgAmountSevenDays(100.0)     // 10x avg → AmountAnomalyRule fires at 3x tier
                .txCountOneHour(15)            // >10 → VelocityRule high tier
                .countryChanged(true)
                .timeSinceLastTxMs(1_800_000L) // 30 min → GeoAnomalyRule high tier
                .mcc("7995")                   // Gambling → MerchantCategoryRule fires
                .deviceFingerprint("fp-new-device")
                .deviceHash("fp-old-device")   // Different → DeviceChangeRule fires
                .timestampHour(3)              // 3 AM UTC → TimeAnomalyRule fires
                .build();
    }

    /** Transaction with medium velocity only. */
    public static EnrichedTransaction mediumVelocityTransaction() {
        return builder()
                .txId("tx-velocity-medium-001")
                .txCountOneHour(7)
                .build();
    }

    /** Transaction with country change but outside 1-hour window. */
    public static EnrichedTransaction geoChangeLowRiskTransaction() {
        return builder()
                .txId("tx-geo-low-001")
                .countryChanged(true)
                .timeSinceLastTxMs(7_200_000L) // 2 hours — below high-risk threshold
                .build();
    }

    /** Transaction with country change within 1-hour window (impossible speed). */
    public static EnrichedTransaction geoChangeHighRiskTransaction() {
        return builder()
                .txId("tx-geo-high-001")
                .countryChanged(true)
                .timeSinceLastTxMs(900_000L) // 15 minutes — impossible travel
                .build();
    }

    /** Transaction with amount exactly 2x average. */
    public static EnrichedTransaction twoXAmountTransaction() {
        return builder()
                .txId("tx-amount-2x-001")
                .amount(200.0)
                .avgAmountSevenDays(100.0)
                .build();
    }

    /** Transaction with amount exactly 3x average. */
    public static EnrichedTransaction threeXAmountTransaction() {
        return builder()
                .txId("tx-amount-3x-001")
                .amount(300.0)
                .avgAmountSevenDays(100.0)
                .build();
    }

    /** Transaction at 3 AM UTC (suspicious hour). */
    public static EnrichedTransaction earlyMorningTransaction() {
        return builder()
                .txId("tx-early-am-001")
                .timestampHour(3)
                .build();
    }

    /** Transaction at 10 AM UTC (normal hour). */
    public static EnrichedTransaction daytimeTransaction() {
        return builder()
                .txId("tx-daytime-001")
                .timestampHour(10)
                .build();
    }

    /** Transaction with high-risk gambling MCC. */
    public static EnrichedTransaction gamblingTransaction() {
        return builder()
                .txId("tx-gambling-001")
                .mcc("7995")
                .build();
    }

    /** Transaction with a device change. */
    public static EnrichedTransaction deviceChangedTransaction() {
        return builder()
                .txId("tx-device-change-001")
                .deviceFingerprint("fp-new")
                .deviceHash("fp-old")
                .build();
    }

    /** Transaction with the same device. */
    public static EnrichedTransaction sameDeviceTransaction() {
        return builder()
                .txId("tx-same-device-001")
                .deviceFingerprint("fp-same")
                .deviceHash("fp-same")
                .build();
    }

    // -------------------------------------------------------------------------
    // Fluent Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private String txId = "tx-test-default";
        private String cardId = "card-001";
        private String merchantId = "merchant-001";
        private double amount = 100.0;
        private String currency = "USD";
        private String mcc = "5411";
        private Channel channel = Channel.ECOM;
        private String country = "US";
        private String deviceFingerprint = null;
        private long timestamp = defaultTimestampAtHour(10);
        private int txCountOneHour = 1;
        private int txCountSixHours = 3;
        private int txCountTwentyFourHours = 5;
        private double avgAmountSevenDays = 100.0;
        private int distinctMerchantsTwentyFourHours = 2;
        private boolean countryChanged = false;
        private long timeSinceLastTxMs = 3_600_000L;
        private double velocityScore = 0.0;
        private String deviceHash = null;

        private Builder() {
        }

        public Builder txId(String txId) { this.txId = txId; return this; }
        public Builder cardId(String cardId) { this.cardId = cardId; return this; }
        public Builder merchantId(String merchantId) { this.merchantId = merchantId; return this; }
        public Builder amount(double amount) { this.amount = amount; return this; }
        public Builder avgAmountSevenDays(double avg) { this.avgAmountSevenDays = avg; return this; }
        public Builder txCountOneHour(int count) { this.txCountOneHour = count; return this; }
        public Builder txCountSixHours(int count) { this.txCountSixHours = count; return this; }
        public Builder txCountTwentyFourHours(int count) { this.txCountTwentyFourHours = count; return this; }
        public Builder distinctMerchantsTwentyFourHours(int count) { this.distinctMerchantsTwentyFourHours = count; return this; }
        public Builder countryChanged(boolean changed) { this.countryChanged = changed; return this; }
        public Builder timeSinceLastTxMs(long ms) { this.timeSinceLastTxMs = ms; return this; }
        public Builder velocityScore(double score) { this.velocityScore = score; return this; }
        public Builder mcc(String mcc) { this.mcc = mcc; return this; }
        public Builder channel(Channel channel) { this.channel = channel; return this; }
        public Builder country(String country) { this.country = country; return this; }
        public Builder deviceFingerprint(String fp) { this.deviceFingerprint = fp; return this; }
        public Builder deviceHash(String hash) { this.deviceHash = hash; return this; }

        public Builder timestampHour(int hourUtc) {
            this.timestamp = defaultTimestampAtHour(hourUtc);
            return this;
        }

        public Builder timestamp(long epochMs) {
            this.timestamp = epochMs;
            return this;
        }

        public EnrichedTransaction build() {
            Location location = Location.newBuilder()
                    .setLat(40.7128)
                    .setLon(-74.0060)
                    .setCountry(country)
                    .build();

            return EnrichedTransaction.newBuilder()
                    .setTxId(txId)
                    .setCardId(cardId)
                    .setMerchantId(merchantId)
                    .setAmount(amount)
                    .setCurrency(currency)
                    .setMcc(mcc)
                    .setChannel(channel)
                    .setLocation(location)
                    .setDeviceFingerprint(deviceFingerprint)
                    .setTimestamp(timestamp)
                    .setTxCountOneHour(txCountOneHour)
                    .setTxCountSixHours(txCountSixHours)
                    .setTxCountTwentyFourHours(txCountTwentyFourHours)
                    .setAvgAmountSevenDays(avgAmountSevenDays)
                    .setDistinctMerchantsTwentyFourHours(distinctMerchantsTwentyFourHours)
                    .setCountryChanged(countryChanged)
                    .setTimeSinceLastTxMs(timeSinceLastTxMs)
                    .setVelocityScore(velocityScore)
                    .setDeviceHash(deviceHash)
                    .build();
        }

        private static long defaultTimestampAtHour(int hourUtc) {
            return LocalDateTime.of(2024, 6, 15, hourUtc, 30)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
        }
    }
}
