package com.cosmos.fraud.scoring.ml;

import java.io.InputStream;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.cosmos.fraud.avro.EnrichedTransaction;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.FloatBuffer;
import java.util.Map;

@Component
public class OnnxModelInferenceService implements ModelInferenceService {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelInferenceService.class);

    /**
     * Feature vector positions (must match training pipeline):
     * 0: amount
     * 1: avgAmountSevenDays
     * 2: txCountOneHour
     * 3: txCountSixHours
     * 4: txCountTwentyFourHours
     * 5: distinctMerchantsTwentyFourHours
     * 6: velocityScore
     * 7: countryChanged (0/1)
     * 8: timeSinceLastTxMs (normalised to hours)
     */
    private static final int FEATURE_COUNT = 9;
    private static final String INPUT_NODE_NAME = "input";
    private static final String OUTPUT_NODE_NAME = "output";

    @Value("${model.onnx.path}")
    private Resource modelResource;

    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private volatile boolean modelAvailable = false;

    @PostConstruct
    public void loadModel() {
        try {
            if (!modelResource.exists()) {
                log.warn("ONNX model not found at '{}'. Scoring will fall back to rules-only mode.",
                        modelResource.getDescription());
                return;
            }
            ortEnvironment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

            try (InputStream is = modelResource.getInputStream()) {
                byte[] modelBytes = is.readAllBytes();
                ortSession = ortEnvironment.createSession(modelBytes, opts);
            }
            modelAvailable = true;
            log.info("ONNX model loaded successfully from '{}'", modelResource.getDescription());
        } catch (Exception e) {
            log.warn("Failed to load ONNX model from '{}'. Falling back to rules-only mode. Cause: {}",
                    modelResource.getDescription(), e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (ortSession != null) {
                ortSession.close();
            }
            if (ortEnvironment != null) {
                ortEnvironment.close();
            }
        } catch (Exception e) {
            log.error("Error closing ORT session/environment", e);
        }
    }

    @Override
    public Optional<Double> predict(EnrichedTransaction transaction) {
        if (!modelAvailable || ortSession == null) {
            return Optional.empty();
        }

        try {
            float[] features = extractFeatures(transaction);
            FloatBuffer fb = FloatBuffer.wrap(features);
            long[] shape = {1L, FEATURE_COUNT};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, fb, shape);
                 OrtSession.Result result = ortSession.run(Map.of(INPUT_NODE_NAME, inputTensor))) {

                OnnxTensor outputTensor = (OnnxTensor) result.get(OUTPUT_NODE_NAME).orElseThrow();
                float[][] outputData = (float[][]) outputTensor.getValue();
                double probability = outputData[0][0];
                // Clamp to [0, 1]
                probability = Math.max(0.0, Math.min(1.0, probability));
                return Optional.of(probability);
            }
        } catch (Exception e) {
            log.error("ONNX inference failed for txId={}: {}", transaction.getTxId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private float[] extractFeatures(EnrichedTransaction tx) {
        float[] features = new float[FEATURE_COUNT];
        features[0] = (float) tx.getAmount();
        features[1] = (float) tx.getAvgAmountSevenDays();
        features[2] = tx.getTxCountOneHour();
        features[3] = tx.getTxCountSixHours();
        features[4] = tx.getTxCountTwentyFourHours();
        features[5] = tx.getDistinctMerchantsTwentyFourHours();
        features[6] = (float) tx.getVelocityScore();
        features[7] = tx.getCountryChanged() ? 1.0f : 0.0f;
        // Convert ms to hours for normalisation
        features[8] = (float) (tx.getTimeSinceLastTxMs() / 3_600_000.0);
        return features;
    }
}
