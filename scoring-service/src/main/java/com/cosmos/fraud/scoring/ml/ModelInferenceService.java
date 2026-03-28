package com.cosmos.fraud.scoring.ml;

import java.util.Optional;

import com.cosmos.fraud.avro.EnrichedTransaction;

public interface ModelInferenceService {

    /**
     * Runs model inference on the given transaction and returns a fraud probability
     * in the range [0.0, 1.0].
     *
     * @param transaction the enriched transaction to score
     * @return an Optional containing the predicted fraud probability, or empty if
     *         the model is unavailable or inference fails
     */
    Optional<Double> predict(EnrichedTransaction transaction);
}
