package com.cosmos.fraud.threeds.dto;

/**
 * Browser information collected during 3DS authentication for browser-based flows.
 */
public record BrowserInfo(
        String userAgent,
        String acceptHeader,
        String language,
        int colorDepth,
        int screenHeight,
        int screenWidth,
        int timezone,
        boolean javaEnabled,
        boolean javascriptEnabled
) {
}
