package com.cosmos.fraud.scoring.rule;

import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

@Component
public class DeviceChangeRule implements Rule {

    private static final int DEVICE_CHANGE_SCORE = 20;
    private static final int NO_RISK_SCORE = 0;

    @Override
    public String name() {
        return "DEVICE_CHANGE";
    }

    @Override
    public int evaluate(EnrichedTransaction transaction) {
        CharSequence deviceHash = transaction.getDeviceHash();
        CharSequence deviceFingerprint = transaction.getDeviceFingerprint();

        // Only flag if a known device hash exists and differs from current fingerprint
        if (deviceHash != null && deviceFingerprint != null) {
            if (!deviceHash.toString().equals(deviceFingerprint.toString())) {
                return DEVICE_CHANGE_SCORE;
            }
        }
        return NO_RISK_SCORE;
    }
}
