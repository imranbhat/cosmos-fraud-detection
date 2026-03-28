package com.cosmos.fraud.modelserving.controller;

import com.cosmos.fraud.modelserving.service.ModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ModelController.class)
class ModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ModelService modelService;

    // --- /v1/model/predict ---

    @Test
    @DisplayName("POST /v1/model/predict returns probability when features are valid")
    void predict_validFeatures_returnsProbability() throws Exception {
        float[] features = {0.1f, 0.5f, 1.2f, -0.3f, 2.0f};
        float expectedProbability = 0.87f;

        when(modelService.predict(any(float[].class))).thenReturn(expectedProbability);

        mockMvc.perform(post("/v1/model/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("features", features))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(expectedProbability))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("POST /v1/model/predict returns 400 when features array is null")
    void predict_nullFeatures_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/model/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"features\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/model/predict returns 400 when features array is empty")
    void predict_emptyFeatures_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/model/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"features\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @DisplayName("POST /v1/model/predict returns 503 when model is not loaded")
    void predict_modelNotLoaded_returnsServiceUnavailable() throws Exception {
        float[] features = {0.1f, 0.5f};

        when(modelService.predict(any(float[].class)))
                .thenThrow(new ModelService.ModelNotLoadedException("Model is not loaded"));

        mockMvc.perform(post("/v1/model/predict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("features", features))))
                .andExpect(status().isInternalServerError());
    }

    // --- /v1/model/reload ---

    @Test
    @DisplayName("POST /v1/model/reload returns status reloaded on success")
    void reload_success_returnsReloadedStatus() throws Exception {
        doNothing().when(modelService).reload();

        mockMvc.perform(post("/v1/model/reload")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reloaded"));

        verify(modelService).reload();
    }

    @Test
    @DisplayName("POST /v1/model/reload returns 500 when reload fails")
    void reload_failure_returnsInternalServerError() throws Exception {
        doThrow(new ModelService.ModelLoadException("Load failed", new RuntimeException()))
                .when(modelService).reload();

        mockMvc.perform(post("/v1/model/reload")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    // --- /v1/model/health ---

    @Test
    @DisplayName("GET /v1/model/health returns UP when model is loaded")
    void health_modelLoaded_returnsUp() throws Exception {
        when(modelService.isModelLoaded()).thenReturn(true);

        mockMvc.perform(get("/v1/model/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.modelLoaded").value(true));
    }

    @Test
    @DisplayName("GET /v1/model/health returns DOWN with 503 when model is not loaded")
    void health_modelNotLoaded_returnsDown() throws Exception {
        when(modelService.isModelLoaded()).thenReturn(false);

        mockMvc.perform(get("/v1/model/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.modelLoaded").value(false));
    }
}
