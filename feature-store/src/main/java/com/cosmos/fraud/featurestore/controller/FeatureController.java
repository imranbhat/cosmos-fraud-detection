package com.cosmos.fraud.featurestore.controller;

import com.cosmos.fraud.featurestore.event.TransactionEvent;
import com.cosmos.fraud.featurestore.model.CardFeatures;
import com.cosmos.fraud.featurestore.service.FeatureService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing feature read/update endpoints.
 */
@RestController
@RequestMapping("/v1/features")
public class FeatureController {

    private static final Logger log = LoggerFactory.getLogger(FeatureController.class);

    private final FeatureService featureService;

    public FeatureController(FeatureService featureService) {
        this.featureService = featureService;
    }

    /**
     * Returns the current {@link CardFeatures} for the given card.
     * Responds with defaults (HTTP 200) when no data has been stored yet.
     *
     * @param cardId the card identifier
     * @return feature snapshot
     */
    @GetMapping("/{cardId}")
    public ResponseEntity<CardFeatures> getFeatures(@PathVariable String cardId) {
        log.debug("GET /v1/features/{}", cardId);
        CardFeatures features = featureService.getFeatures(cardId);
        return ResponseEntity.ok(features);
    }

    /**
     * Atomically updates features for the given card based on the incoming
     * {@link TransactionEvent} and returns the refreshed feature snapshot.
     *
     * @param cardId the card identifier
     * @param event  the transaction event payload
     * @return updated feature snapshot
     */
    @PostMapping("/{cardId}/update")
    public ResponseEntity<CardFeatures> updateFeatures(
            @PathVariable String cardId,
            @Valid @RequestBody TransactionEvent event) {
        log.debug("POST /v1/features/{}/update", cardId);
        CardFeatures updated = featureService.updateFeatures(cardId, event);
        return ResponseEntity.ok(updated);
    }
}
