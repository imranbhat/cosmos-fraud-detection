package com.cosmos.fraud.featurestore.service;

import com.cosmos.fraud.featurestore.event.TransactionEvent;
import com.cosmos.fraud.featurestore.model.CardFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for reading and atomically updating card features in Redis.
 *
 * <p>Key schema:
 * <ul>
 *   <li>{@code features:{cardId}} – Redis hash containing all aggregated fields</li>
 *   <li>{@code txset:{cardId}}   – Sorted set of timestamps used for sliding-window tx counts</li>
 *   <li>{@code merchants:{cardId}} – HyperLogLog tracking distinct merchants (24 h window)</li>
 * </ul>
 */
@Service
public class FeatureService {

    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    // Hash field names kept in sync with the Lua script
    static final String FIELD_TX_COUNT_1H    = "tx_count_1h";
    static final String FIELD_TX_COUNT_6H    = "tx_count_6h";
    static final String FIELD_TX_COUNT_24H   = "tx_count_24h";
    static final String FIELD_AVG_AMOUNT_7D  = "avg_amount_7d";
    static final String FIELD_DISTINCT_MERCH = "distinct_merchants_24h";
    static final String FIELD_LAST_COUNTRY   = "last_country";
    static final String FIELD_COUNTRY_CHANGED = "country_changed";
    static final String FIELD_TIME_SINCE_TX  = "time_since_last_tx_ms";
    static final String FIELD_DEVICE_HASH    = "device_hash";
    static final String FIELD_VELOCITY_SCORE = "velocity_score";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> updateFeaturesScript;

    public FeatureService(StringRedisTemplate redisTemplate,
                          RedisScript<String> updateFeaturesScript) {
        this.redisTemplate = redisTemplate;
        this.updateFeaturesScript = updateFeaturesScript;
    }

    /**
     * Reads pre-computed features for {@code cardId} from the Redis hash.
     * Returns a default (zero-value) {@link CardFeatures} when no data is found.
     */
    public CardFeatures getFeatures(String cardId) {
        String hashKey = featuresKey(cardId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(hashKey);

        if (entries == null || entries.isEmpty()) {
            log.debug("No features found for card {}, returning defaults", cardId);
            return CardFeatures.defaultFor(cardId);
        }

        return mapToCardFeatures(cardId, entries);
    }

    /**
     * Executes the Lua update script atomically against Redis and returns the
     * refreshed {@link CardFeatures} for the given card.
     */
    public CardFeatures updateFeatures(String cardId, TransactionEvent event) {
        String featKey     = featuresKey(cardId);
        String txSetKey    = txSetKey(cardId);
        String merchantKey = merchantKey(cardId);

        List<String> keys = List.of(featKey, txSetKey, merchantKey);
        String[] argv = {
            String.valueOf(event.timestampMs()),
            event.amount().toPlainString(),
            event.merchantId(),
            event.country() != null ? event.country() : "",
            event.deviceHash() != null ? event.deviceHash() : ""
        };

        log.debug("Executing feature-update Lua script for card {}", cardId);
        redisTemplate.execute(updateFeaturesScript, keys, (Object[]) argv);

        return getFeatures(cardId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private CardFeatures mapToCardFeatures(String cardId, Map<Object, Object> entries) {
        int txCount1h    = parseInt(entries, FIELD_TX_COUNT_1H);
        int txCount6h    = parseInt(entries, FIELD_TX_COUNT_6H);
        int txCount24h   = parseInt(entries, FIELD_TX_COUNT_24H);
        double avgAmt7d  = parseDouble(entries, FIELD_AVG_AMOUNT_7D);
        int distMerch    = parseInt(entries, FIELD_DISTINCT_MERCH);
        String lastCtry  = parseString(entries, FIELD_LAST_COUNTRY);
        boolean ctryChg  = parseBoolean(entries, FIELD_COUNTRY_CHANGED);
        long timeSinceTx = parseLong(entries, FIELD_TIME_SINCE_TX);
        String devHash   = parseString(entries, FIELD_DEVICE_HASH);
        double velScore  = parseDouble(entries, FIELD_VELOCITY_SCORE);

        return new CardFeatures(
                cardId, txCount1h, txCount6h, txCount24h,
                avgAmt7d, distMerch, lastCtry, ctryChg,
                timeSinceTx, devHash, velScore);
    }

    private static int parseInt(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) return 0;
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseLong(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) return -1L;
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return -1L; }
    }

    private static double parseDouble(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) return 0.0;
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static boolean parseBoolean(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        return val != null && ("1".equals(val.toString()) || "true".equalsIgnoreCase(val.toString()));
    }

    private static String parseString(Map<Object, Object> map, String field) {
        Object val = map.get(field);
        return val == null || val.toString().isEmpty() ? null : val.toString();
    }

    static String featuresKey(String cardId)  { return "features:" + cardId; }
    static String txSetKey(String cardId)     { return "txset:" + cardId; }
    static String merchantKey(String cardId)  { return "merchants:" + cardId; }
}
