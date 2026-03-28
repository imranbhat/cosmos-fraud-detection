package com.cosmos.fraud.modelserving.controller;

import com.cosmos.fraud.modelserving.service.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/model")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * POST /v1/model/predict
     * Accepts a JSON body with a "features" float array and returns a fraud probability score.
     *
     * Request:  { "features": [0.1, 0.5, 1.2, ...] }
     * Response: { "probability": 0.87 }
     */
    @PostMapping("/predict")
    public ResponseEntity<PredictResponse> predict(@RequestBody PredictRequest request) {
        if (request.features() == null || request.features().length == 0) {
            return ResponseEntity.badRequest().body(
                    new PredictResponse(null, "Feature array must not be null or empty"));
        }

        float probability = modelService.predict(request.features());
        log.debug("Prediction complete: probability={}", probability);
        return ResponseEntity.ok(new PredictResponse(probability, null));
    }

    /**
     * POST /v1/model/reload
     * Triggers a hot reload of the ONNX model file from disk.
     *
     * Response: { "status": "reloaded" }
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reload() {
        log.info("Model reload requested via API");
        modelService.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }

    /**
     * GET /v1/model/health
     * Returns whether the model is loaded and ready for inference.
     *
     * Response: { "status": "UP" | "DOWN", "modelLoaded": true | false }
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean loaded = modelService.isModelLoaded();
        HealthResponse response = new HealthResponse(
                loaded ? "UP" : "DOWN",
                loaded
        );
        return loaded
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }

    // --- Request / Response records ---

    public record PredictRequest(float[] features) {}

    public record PredictResponse(Float probability, String error) {}

    public record HealthResponse(String status, boolean modelLoaded) {}
}
