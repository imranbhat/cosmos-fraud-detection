package com.cosmos.fraud.stream.model;

import org.apache.avro.reflect.AvroDefault;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a raw transaction event as read from the "transactions.raw" Kafka topic.
 * <p>
 * This is the entry-point event for the stream-processing pipeline. It mirrors
 * the fields published by the ingestion-service and is serialised/deserialised
 * using Avro (see {@link com.cosmos.fraud.stream.serialization.AvroDeserializationSchema}).
 */
public final class TransactionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Globally-unique transaction identifier (ULID). */
    private String txId;

    /** Card identifier associated with this transaction. */
    private String cardId;

    /** Merchant identifier. */
    private String merchantId;

    /** Transaction amount. */
    private BigDecimal amount;

    /** ISO-4217 currency code (e.g. "USD"). */
    private String currency;

    /** Merchant category code. */
    private String mcc;

    /** Originating channel (e.g. "ONLINE", "POS", "ATM"). */
    private String channel;

    /** Geo-latitude of the transaction (optional). */
    private Double latitude;

    /** Geo-longitude of the transaction (optional). */
    private Double longitude;

    /** ISO-3166-1 alpha-2 country code. */
    private String country;

    /** Device fingerprint collected at transaction time (optional). */
    private String deviceFingerprint;

    /** Wall-clock timestamp at which the event was emitted (epoch millis). */
    private long eventTimestamp;

    // -----------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------

    /** Required by Avro and Flink serialisation. */
    public TransactionEvent() {
    }

    public TransactionEvent(
            String txId,
            String cardId,
            String merchantId,
            BigDecimal amount,
            String currency,
            String mcc,
            String channel,
            Double latitude,
            Double longitude,
            String country,
            String deviceFingerprint,
            long eventTimestamp) {
        this.txId = Objects.requireNonNull(txId, "txId");
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.mcc = Objects.requireNonNull(mcc, "mcc");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.latitude = latitude;
        this.longitude = longitude;
        this.country = Objects.requireNonNull(country, "country");
        this.deviceFingerprint = deviceFingerprint;
        this.eventTimestamp = eventTimestamp;
    }

    // -----------------------------------------------------------------
    // Getters / Setters (Flink POJO rules require mutable fields)
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

    @Override
    public String toString() {
        return "TransactionEvent{txId='" + txId + "', cardId='" + cardId
                + "', merchantId='" + merchantId + "', amount=" + amount
                + ", currency='" + currency + "', eventTimestamp=" + eventTimestamp + '}';
    }
}
