package com.cosmos.fraud.stream.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link TransactionEvent} enriched with real-time feature-store data.
 * <p>
 * Produced by {@link com.cosmos.fraud.stream.function.FeatureEnrichmentAsyncFunction}
 * and published to the "transactions.enriched" Kafka topic.
 */
public final class EnrichedTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---- Fields copied from the original TransactionEvent ----
    private String txId;
    private String cardId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String mcc;
    private String channel;
    private Double latitude;
    private Double longitude;
    private String country;
    private String deviceFingerprint;
    private long eventTimestamp;

    // ---- Enrichment fields from the feature store ----

    /**
     * Free-form feature map returned by the feature store (e.g. velocity counts,
     * risk scores, spending averages).
     */
    private Map<String, Object> features = new HashMap<>();

    /** Milliseconds spent calling the feature-store. */
    private long featureLatencyMs;

    /** Timestamp (epoch millis) at which enrichment completed. */
    private long enrichedAt;

    // -----------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------

    /** Required by Avro and Flink serialisation. */
    public EnrichedTransaction() {
    }

    /**
     * Convenience factory: copies all fields from a {@link TransactionEvent}.
     *
     * @param source the original transaction event
     * @return a new {@code EnrichedTransaction} with feature map still empty
     */
    public static EnrichedTransaction from(TransactionEvent source) {
        Objects.requireNonNull(source, "source");
        EnrichedTransaction et = new EnrichedTransaction();
        et.txId = source.getTxId();
        et.cardId = source.getCardId();
        et.merchantId = source.getMerchantId();
        et.amount = source.getAmount();
        et.currency = source.getCurrency();
        et.mcc = source.getMcc();
        et.channel = source.getChannel();
        et.latitude = source.getLatitude();
        et.longitude = source.getLongitude();
        et.country = source.getCountry();
        et.deviceFingerprint = source.getDeviceFingerprint();
        et.eventTimestamp = source.getEventTimestamp();
        et.enrichedAt = System.currentTimeMillis();
        return et;
    }

    // -----------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMcc() { return mcc; }
    public void setMcc(String mcc) { this.mcc = mcc; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }

    public long getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(long eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public Map<String, Object> getFeatures() { return features; }
    public void setFeatures(Map<String, Object> features) { this.features = features; }

    public long getFeatureLatencyMs() { return featureLatencyMs; }
    public void setFeatureLatencyMs(long featureLatencyMs) { this.featureLatencyMs = featureLatencyMs; }

    public long getEnrichedAt() { return enrichedAt; }
    public void setEnrichedAt(long enrichedAt) { this.enrichedAt = enrichedAt; }

    @Override
    public String toString() {
        return "EnrichedTransaction{txId='" + txId + "', cardId='" + cardId
                + "', merchantId='" + merchantId + "', features=" + features
                + ", featureLatencyMs=" + featureLatencyMs + '}';
    }
}
