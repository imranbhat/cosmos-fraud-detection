package com.cosmos.fraud.featurestore.service;

import com.cosmos.fraud.featurestore.event.TransactionEvent;
import com.cosmos.fraud.featurestore.model.CardFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<String> updateFeaturesScript;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private FeatureService featureService;

    @BeforeEach
    void setUp() {
        featureService = new FeatureService(redisTemplate, updateFeaturesScript);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
    }

    // -----------------------------------------------------------------------
    // getFeatures – no data
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getFeatures returns defaults when Redis hash is empty")
    void getFeatures_noData_returnsDefaults() {
        String cardId = "card-001";
        when(hashOps.entries(FeatureService.featuresKey(cardId)))
                .thenReturn(Collections.emptyMap());

        CardFeatures result = featureService.getFeatures(cardId);

        assertThat(result.cardId()).isEqualTo(cardId);
        assertThat(result.txCountOneHour()).isZero();
        assertThat(result.txCountSixHours()).isZero();
        assertThat(result.txCountTwentyFourHours()).isZero();
        assertThat(result.avgAmountSevenDays()).isZero();
        assertThat(result.distinctMerchantsTwentyFourHours()).isZero();
        assertThat(result.lastCountry()).isNull();
        assertThat(result.countryChanged()).isFalse();
        assertThat(result.timeSinceLastTxMs()).isEqualTo(-1L);
        assertThat(result.deviceHash()).isNull();
        assertThat(result.velocityScore()).isZero();
    }

    @Test
    @DisplayName("getFeatures returns defaults when Redis returns null map")
    void getFeatures_nullMap_returnsDefaults() {
        String cardId = "card-002";
        when(hashOps.entries(FeatureService.featuresKey(cardId))).thenReturn(null);

        CardFeatures result = featureService.getFeatures(cardId);

        assertThat(result).isEqualTo(CardFeatures.defaultFor(cardId));
    }

    // -----------------------------------------------------------------------
    // getFeatures – maps Redis hash fields correctly
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getFeatures maps all Redis hash fields to CardFeatures")
    void getFeatures_withData_mapsAllFields() {
        String cardId = "card-003";
        Map<Object, Object> redisEntries = buildFullHashMap();

        when(hashOps.entries(FeatureService.featuresKey(cardId))).thenReturn(redisEntries);

        CardFeatures result = featureService.getFeatures(cardId);

        assertThat(result.cardId()).isEqualTo(cardId);
        assertThat(result.txCountOneHour()).isEqualTo(3);
        assertThat(result.txCountSixHours()).isEqualTo(7);
        assertThat(result.txCountTwentyFourHours()).isEqualTo(15);
        assertThat(result.avgAmountSevenDays()).isEqualTo(120.50);
        assertThat(result.distinctMerchantsTwentyFourHours()).isEqualTo(5);
        assertThat(result.lastCountry()).isEqualTo("US");
        assertThat(result.countryChanged()).isTrue();
        assertThat(result.timeSinceLastTxMs()).isEqualTo(300_000L);
        assertThat(result.deviceHash()).isEqualTo("abc123");
        assertThat(result.velocityScore()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("getFeatures handles missing optional fields gracefully")
    void getFeatures_partialData_usesDefaults() {
        String cardId = "card-004";
        Map<Object, Object> partial = new HashMap<>();
        partial.put(FeatureService.FIELD_TX_COUNT_1H, "2");
        // all other fields absent

        when(hashOps.entries(FeatureService.featuresKey(cardId))).thenReturn(partial);

        CardFeatures result = featureService.getFeatures(cardId);

        assertThat(result.txCountOneHour()).isEqualTo(2);
        assertThat(result.txCountSixHours()).isZero();
        assertThat(result.lastCountry()).isNull();
        assertThat(result.countryChanged()).isFalse();
        assertThat(result.timeSinceLastTxMs()).isEqualTo(-1L);
    }

    // -----------------------------------------------------------------------
    // updateFeatures – verifies Redis interactions
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateFeatures executes Lua script with correct keys and args")
    void updateFeatures_executesScriptWithCorrectParameters() {
        String cardId = "card-005";
        long ts = 1_700_000_000_000L;
        TransactionEvent event = new TransactionEvent(
                cardId, "merchant-42", new BigDecimal("99.99"), "GB", "deviceXyz", ts);

        // Script execution returns null (void-like); subsequent getFeatures call
        when(redisTemplate.execute(
                eq(updateFeaturesScript),
                eq(List.of(
                        FeatureService.featuresKey(cardId),
                        FeatureService.txSetKey(cardId),
                        FeatureService.merchantKey(cardId))),
                any(Object[].class)))
                .thenReturn(null);

        // Post-script read returns updated hash
        Map<Object, Object> updatedHash = buildFullHashMap();
        when(hashOps.entries(FeatureService.featuresKey(cardId))).thenReturn(updatedHash);

        CardFeatures result = featureService.updateFeatures(cardId, event);

        // Script was called exactly once
        verify(redisTemplate, times(1)).execute(
                eq(updateFeaturesScript),
                eq(List.of(
                        FeatureService.featuresKey(cardId),
                        FeatureService.txSetKey(cardId),
                        FeatureService.merchantKey(cardId))),
                any(Object[].class));

        // Result is derived from the hash read after the script
        assertThat(result.cardId()).isEqualTo(cardId);
        assertThat(result.txCountOneHour()).isEqualTo(3);
    }

    @Test
    @DisplayName("updateFeatures handles null country and deviceHash gracefully")
    void updateFeatures_nullOptionalFields_doesNotThrow() {
        String cardId = "card-006";
        TransactionEvent event = new TransactionEvent(
                cardId, "merchant-99", new BigDecimal("10.00"), null, null, System.currentTimeMillis());

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);
        when(hashOps.entries(FeatureService.featuresKey(cardId)))
                .thenReturn(Collections.emptyMap());

        CardFeatures result = featureService.updateFeatures(cardId, event);

        assertThat(result).isNotNull();
        assertThat(result.cardId()).isEqualTo(cardId);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<Object, Object> buildFullHashMap() {
        Map<Object, Object> map = new HashMap<>();
        map.put(FeatureService.FIELD_TX_COUNT_1H,    "3");
        map.put(FeatureService.FIELD_TX_COUNT_6H,    "7");
        map.put(FeatureService.FIELD_TX_COUNT_24H,   "15");
        map.put(FeatureService.FIELD_AVG_AMOUNT_7D,  "120.50");
        map.put(FeatureService.FIELD_DISTINCT_MERCH, "5");
        map.put(FeatureService.FIELD_LAST_COUNTRY,   "US");
        map.put(FeatureService.FIELD_COUNTRY_CHANGED,"1");
        map.put(FeatureService.FIELD_TIME_SINCE_TX,  "300000");
        map.put(FeatureService.FIELD_DEVICE_HASH,    "abc123");
        map.put(FeatureService.FIELD_VELOCITY_SCORE, "0.75");
        return map;
    }
}
