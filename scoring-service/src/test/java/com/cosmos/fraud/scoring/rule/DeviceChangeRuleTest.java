package com.cosmos.fraud.scoring.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cosmos.fraud.avro.EnrichedTransaction;
import com.cosmos.fraud.scoring.TestDataFactory;

@DisplayName("DeviceChangeRule")
class DeviceChangeRuleTest {

    private DeviceChangeRule rule;

    @BeforeEach
    void setUp() {
        rule = new DeviceChangeRule();
    }

    @Test
    @DisplayName("returns 0 when current device matches stored device hash")
    void sameDevice_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .deviceFingerprint("fp-abc123")
                .deviceHash("fp-abc123")
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 20 when current device differs from stored device hash")
    void differentDevice_returns20() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .deviceFingerprint("fp-new-device")
                .deviceHash("fp-old-device")
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(20);
    }

    @Test
    @DisplayName("returns 0 when deviceHash is null (first transaction)")
    void nullDeviceHash_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .deviceFingerprint("fp-abc123")
                .deviceHash(null)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 0 when deviceFingerprint is null (no device info in transaction)")
    void nullDeviceFingerprint_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .deviceFingerprint(null)
                .deviceHash("fp-old-device")
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("returns 0 when both deviceFingerprint and deviceHash are null")
    void bothNull_returnsZero() {
        EnrichedTransaction tx = TestDataFactory.builder()
                .deviceFingerprint(null)
                .deviceHash(null)
                .build();
        assertThat(rule.evaluate(tx)).isEqualTo(0);
    }

    @Test
    @DisplayName("rule name is DEVICE_CHANGE")
    void ruleName_isDeviceChange() {
        assertThat(rule.name()).isEqualTo("DEVICE_CHANGE");
    }
}
