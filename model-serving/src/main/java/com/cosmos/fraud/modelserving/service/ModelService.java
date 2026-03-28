package com.cosmos.fraud.modelserving.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    @Value("${model.path:/models/fraud_model.onnx}")
    private String modelPath;

    private OrtEnvironment environment;
    private OrtSession session;
    private final AtomicBoolean modelLoaded = new AtomicBoolean(false);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @PostConstruct
    public void init() {
        try {
            environment = OrtEnvironment.getEnvironment();
            loadModel();
        } catch (Exception e) {
            log.error("Failed to initialize ONNX runtime environment", e);
        }
    }

    private void loadModel() {
        lock.writeLock().lock();
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            session = environment.createSession(modelPath, options);
            modelLoaded.set(true);
            log.info("ONNX model loaded successfully from path: {}", modelPath);
        } catch (OrtException e) {
            modelLoaded.set(false);
            log.error("Failed to load ONNX model from path: {}", modelPath, e);
            throw new ModelLoadException("Failed to load model from: " + modelPath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Run inference on the given feature array and return a fraud probability.
     *
     * @param features flat float array of input features
     * @return fraud probability in range [0.0, 1.0]
     */
    public float predict(float[] features) {
        if (!modelLoaded.get()) {
            throw new ModelNotLoadedException("Model is not loaded. Cannot perform prediction.");
        }

        lock.readLock().lock();
        try {
            String inputName = session.getInputNames().iterator().next();
            long[] shape = {1, features.length};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                    environment, FloatBuffer.wrap(features), shape)) {

                try (OrtSession.Result result = session.run(
                        Collections.singletonMap(inputName, inputTensor))) {

                    float[][] output = (float[][]) result.get(0).getValue();
                    // Assumes model output is [batch][probability], take index 1 for fraud class
                    return output[0].length > 1 ? output[0][1] : output[0][0];
                }
            }
        } catch (OrtException e) {
            log.error("Prediction failed for feature vector of length {}", features.length, e);
            throw new PredictionException("Inference failed", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Hot-reload the ONNX model from disk without restarting the service.
     */
    public void reload() {
        log.info("Reloading model from path: {}", modelPath);
        loadModel();
        log.info("Model reload complete");
    }

    /**
     * Returns true if the model is currently loaded and ready for inference.
     */
    public boolean isModelLoaded() {
        return modelLoaded.get();
    }

    // --- Exception types ---

    public static class ModelLoadException extends RuntimeException {
        public ModelLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ModelNotLoadedException extends RuntimeException {
        public ModelNotLoadedException(String message) {
            super(message);
        }
    }

    public static class PredictionException extends RuntimeException {
        public PredictionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
