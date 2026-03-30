package com.cosmos.fraud.featurestore.controller;

import com.cosmos.fraud.featurestore.event.TransactionEvent;
import com.cosmos.fraud.featurestore.model.CardFeatures;
import com.cosmos.fraud.featurestore.service.FeatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeatureController.class)
class FeatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private FeatureService featureService;

    // -----------------------------------------------------------------------
    // GET /v1/features/{cardId}
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GET /v1/features/{cardId} returns 200 with features for known card")
    void getFeatures_knownCard_returns200WithFeatures() throws Exception {
        String cardId = "card-abc";
        CardFeatures features = new CardFeatures(
                cardId, 5, 12, 30, 250.00, 8, "US", false, 120_000L, "dev-hash-1", 0.65);

        when(featureService.getFeatures(cardId)).thenReturn(features);

        mockMvc.perform(get("/v1/features/{cardId}", cardId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.txCountOneHour").value(5))
                .andExpect(jsonPath("$.txCountSixHours").value(12))
                .andExpect(jsonPath("$.txCountTwentyFourHours").value(30))
                .andExpect(jsonPath("$.avgAmountSevenDays").value(250.00))
                .andExpect(jsonPath("$.distinctMerchantsTwentyFourHours").value(8))
                .andExpect(jsonPath("$.lastCountry").value("US"))
                .andExpect(jsonPath("$.countryChanged").value(false))
                .andExpect(jsonPath("$.timeSinceLastTxMs").value(120_000))
                .andExpect(jsonPath("$.deviceHash").value("dev-hash-1"))
                .andExpect(jsonPath("$.velocityScore").value(0.65));
    }

    @Test
    @DisplayName("GET /v1/features/{cardId} returns 200 with default features for unknown card")
    void getFeatures_unknownCard_returns200WithDefaults() throws Exception {
        String cardId = "card-unknown";
        when(featureService.getFeatures(cardId)).thenReturn(CardFeatures.defaultFor(cardId));

        mockMvc.perform(get("/v1/features/{cardId}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.txCountOneHour").value(0))
                .andExpect(jsonPath("$.txCountSixHours").value(0))
                .andExpect(jsonPath("$.txCountTwentyFourHours").value(0))
                .andExpect(jsonPath("$.avgAmountSevenDays").value(0.0))
                .andExpect(jsonPath("$.distinctMerchantsTwentyFourHours").value(0))
                .andExpect(jsonPath("$.countryChanged").value(false))
                .andExpect(jsonPath("$.timeSinceLastTxMs").value(-1))
                .andExpect(jsonPath("$.velocityScore").value(0.0));
    }

    // -----------------------------------------------------------------------
    // POST /v1/features/{cardId}/update
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /v1/features/{cardId}/update returns 200 with updated features")
    void updateFeatures_validEvent_returns200WithUpdatedFeatures() throws Exception {
        String cardId = "card-xyz";
        long now = System.currentTimeMillis();
        TransactionEvent event = new TransactionEvent(
                cardId, "merchant-01", new BigDecimal("49.99"), "FR", "device-hash-2", now);

        CardFeatures updated = new CardFeatures(
                cardId, 1, 1, 1, 49.99, 1, "FR", true, 500_000L, "device-hash-2", 0.30);

        when(featureService.updateFeatures(cardId, event)).thenReturn(updated);

        mockMvc.perform(post("/v1/features/{cardId}/update", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.txCountOneHour").value(1))
                .andExpect(jsonPath("$.lastCountry").value("FR"))
                .andExpect(jsonPath("$.countryChanged").value(true))
                .andExpect(jsonPath("$.deviceHash").value("device-hash-2"))
                .andExpect(jsonPath("$.velocityScore").value(0.30));
    }

    @Test
    @DisplayName("POST /v1/features/{cardId}/update with missing body returns 400")
    void updateFeatures_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/v1/features/{cardId}/update", "card-err")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
