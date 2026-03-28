package com.cosmos.fraud.threeds.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * REST client for the downstream scoring-service.
 * <p>
 * Uses Spring 6.1 {@link RestClient} for synchronous HTTP calls.
 * Falls back to a high-risk score on any communication error so that
 * 3DS authentication can still make a conservative decision rather than
 * propagating failures upstream.
 */
@Component
public class ScoringClient {

    private static final Logger log = LoggerFactory.getLogger(ScoringClient.class);

    /** Fallback score returned when the scoring service is unavailable. */
    static final int FALLBACK_HIGH_RISK_SCORE = 850;

    private static final String SCORE_PATH = "/v1/score";

    private final RestClient restClient;

    public ScoringClient(@Value("${scoring.service.url:http://localhost:8083}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Requests a fraud risk score from the scoring service.
     *
     * @param cardId     card identifier
     * @param merchantId merchant identifier
     * @param amount     transaction amount
     * @param currency   ISO 4217 currency code
     * @param deviceId   device identifier (may be null)
     * @return {@link ScoringResponse} with the computed risk score
     */
    public ScoringResponse score(String cardId,
                                 String merchantId,
                                 BigDecimal amount,
                                 String currency,
                                 String deviceId) {
        ScoringRequest request = new ScoringRequest(cardId, merchantId, amount, currency, deviceId);
        try {
            ScoringResponse response = restClient.post()
                    .uri(SCORE_PATH)
                    .body(request)
                    .retrieve()
                    .body(ScoringResponse.class);

            if (response == null) {
                log.warn("Scoring service returned null response — using fallback score");
                return fallbackResponse();
            }
            log.debug("Scoring service returned score={} for cardId={}", response.riskScore(), cardId);
            return response;

        } catch (RestClientException ex) {
            log.error("Scoring service call failed for cardId={}: {} — circuit breaker fallback applied",
                    cardId, ex.getMessage());
            return fallbackResponse();
        }
    }

    private ScoringResponse fallbackResponse() {
        return new ScoringResponse(FALLBACK_HIGH_RISK_SCORE, "FALLBACK");
    }

    // ---------------------------------------------------------------------------
    // Internal request / response records (not exposed outside this package)
    // ---------------------------------------------------------------------------

    public record ScoringRequest(
            String cardId,
            String merchantId,
            BigDecimal amount,
            String currency,
            String deviceId
    ) {
    }

    public record ScoringResponse(
            int riskScore,
            String scoreCategory
    ) {
    }
}
