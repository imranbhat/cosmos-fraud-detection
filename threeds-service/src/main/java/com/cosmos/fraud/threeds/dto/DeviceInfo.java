package com.cosmos.fraud.threeds.dto;

/**
 * Device information collected during 3DS authentication.
 */
public record DeviceInfo(
        String deviceId,
        String deviceType,
        String os,
        String osVersion,
        String model
) {
}
