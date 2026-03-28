# ADR-004: ONNX Runtime Sidecar for ML Model Serving

**Status:** Accepted
**Date:** 2024-04-10
**Deciders:** ML Platform, Fraud Engineering, Platform Engineering

---

## Context

The Scoring Service requires low-latency ML model inference as part of the real-time fraud scoring path. The ML model is a gradient-boosted tree (XGBoost) trained offline and deployed to production. Key requirements:

- **Latency** — inference must complete in <10ms p99 to fit within the 50ms end-to-end scoring budget.
- **Language agnosticism** — the model is trained in Python (XGBoost/scikit-learn), but the Scoring Service is Java. The serving layer must bridge these without requiring a Python service in the critical path.
- **Hot-reload** — model updates (new versions) should not require a Scoring Service restart or rolling deployment. Model files should be swappable without downtime.
- **Fallback** — if the model serving layer is unavailable or exceeds its latency budget, scoring should degrade gracefully to the rule engine only.
- **Isolation** — model inference should not destabilize the Scoring Service JVM (e.g., through native memory pressure or GC interference).

---

## Decision

Deploy the ML model as an **ONNX Runtime sidecar** co-located with each Scoring Service pod. The Scoring Service calls the sidecar over local gRPC (`localhost`), and the sidecar loads and serves the ONNX model file.

---

## Architecture

```
┌──────────────────────────────────────┐
│  Scoring Service Pod                 │
│                                      │
│  ┌──────────────────┐                │
│  │  Scoring Service  │               │
│  │  (Java / JVM)     │               │
│  │                   │               │
│  │  RuleEngine       │               │
│  │  ModelClient ─────┼─► gRPC        │
│  └──────────────────┘    (localhost) │
│                              │       │
│  ┌───────────────────────────▼──┐    │
│  │  ONNX Runtime Sidecar        │    │
│  │  (C++ / ONNX Runtime)        │    │
│  │                              │    │
│  │  InferenceSession            │    │
│  │  /models/fraud-v4.2.1.onnx  │    │
│  │                              │    │
│  │  File watcher → hot-reload   │    │
│  └──────────────────────────────┘    │
│                                      │
│  Shared Volume: /models/             │
└──────────────────────────────────────┘
         │
         │ Model file update
         ▼
   Kubernetes ConfigMap / PVC
   (model files pushed by ML pipeline)
```

---

## Rationale

### 1. Language Agnosticism via ONNX Format

The Open Neural Network Exchange (ONNX) format is a portable, vendor-neutral model representation. XGBoost, LightGBM, scikit-learn, and PyTorch models can all be exported to ONNX using well-maintained conversion libraries (`sklearn2pmml`, `xgboost`'s built-in ONNX export, `torch.onnx.export`).

This eliminates the need to:
- Maintain a Python serving service in the critical authorization path.
- Embed a Python interpreter in the Java service (Jython/GraalPy add complexity and performance overhead).
- Re-implement the model in Java.

The ML team can continue training in Python; the artifact they produce is a `.onnx` file.

### 2. Low Latency

ONNX Runtime is a high-performance C++ inference engine optimized for low-latency serving. Benchmarks on our feature vector size (~25 float features, gradient-boosted tree model):

| Engine | p50 | p99 | Notes |
|---|---|---|---|
| Python XGBoost (subprocess call) | 6ms | 22ms | Inter-process overhead dominates |
| Java gRPC → Python TorchServe | 4ms | 14ms | Network serialization overhead |
| Java gRPC → ONNX Runtime sidecar (localhost) | 0.4ms | 1.8ms | loopback gRPC, minimal serialization |
| ONNX Runtime embedded (JNI) | 0.3ms | 1.5ms | Considered but rejected (see below) |

The sidecar approach adds ~0.1ms vs. JNI embedding, which is an acceptable tradeoff for the isolation benefits.

### 3. Model Hot-Reload Without Service Restart

The ONNX Runtime sidecar watches the `/models/` shared volume for changes. When a new model file is detected:

1. The new `InferenceSession` is loaded and validated.
2. Traffic is routed to the new session atomically (swap pointer).
3. The old session is released after all in-flight requests complete.
4. A health check endpoint confirms the active model version.

This allows the ML pipeline to publish a new model version by writing a new `.onnx` file to the shared volume (via a Kubernetes PVC or ConfigMap update), without any Scoring Service or sidecar restart.

The active model version is emitted in every `fraud.decisions` event for traceability.

### 4. Fallback to Rule Engine

The Scoring Service wraps the gRPC call to the sidecar in a circuit breaker:

```
if (modelClient.isAvailable() && modelClient.inferWithTimeout(features, 8ms)) {
    return combineRuleAndModelScore(ruleResult, modelScore);
} else {
    metrics.increment("model.fallback");
    return ruleResult.withFallback(true);
}
```

Conditions triggering fallback:
- Sidecar gRPC call exceeds 8ms timeout.
- Sidecar returns an error (5xx status).
- Circuit breaker is open (>10% error rate in last 30s).

Fallback is transparent to callers. The `fallback: true` flag in the response allows post-hoc analysis of decisions made without ML scoring.

### 5. JVM Isolation

Running the ONNX Runtime as a sidecar container keeps C++ native memory separate from the JVM heap. Concerns avoided:

- JNI memory leaks or native heap fragmentation do not affect JVM GC behavior.
- A crash in the sidecar is isolated to that container; the Scoring Service pod continues running and falls back to rules.
- Resource limits (CPU, memory) can be set independently for the JVM and the ONNX Runtime containers.

---

## Alternatives Considered

### Option A: Python TorchServe / BentoML in Critical Path

Deploy a separate Python model serving service; Scoring Service calls it over the network.

**Rejected because:**
- Network hop adds 5–15ms latency (serialization, DNS, TCP), exceeding the 10ms inference budget.
- Introduces a dependency on a separate service in the authorization critical path; its unavailability requires fallback handling anyway.
- Python serving containers are heavier than the ONNX Runtime sidecar.

### Option B: JNI Embedding (ONNX Runtime Java Bindings)

Embed ONNX Runtime directly in the Scoring Service JVM using the `onnxruntime` Java library.

**Rejected because:**
- JNI native memory is not managed by the JVM; leaks or fragmentation can trigger unexpected JVM crashes.
- A native crash in the ONNX Runtime JNI layer kills the entire Scoring Service JVM process.
- Debugging native crashes in a JVM context is significantly harder than debugging a separate sidecar process.
- The latency difference vs. a localhost gRPC sidecar is <0.2ms — not worth the isolation tradeoff.

### Option C: TensorFlow Serving

**Rejected because:**
- TF Serving is optimized for TensorFlow/Keras models. XGBoost-to-TF conversion adds complexity and potential model accuracy discrepancy.
- Heavier resource footprint than ONNX Runtime for tree-based models.

### Option D: Rule Engine Only (No ML)

**Rejected because:**
- Rule engines cannot capture non-linear feature interactions. The XGBoost model detects patterns (e.g., moderate velocity + slightly elevated amount + new merchant + certain MCC combinations) that no practical set of discrete rules can encode.
- Fraud detection precision at acceptable recall rates requires ML. Rules alone would require an unacceptably high decline rate to achieve the same fraud catch rate.

---

## Consequences

**Positive:**
- Sub-2ms p99 inference latency, comfortably within the scoring budget.
- ML team retains Python-native model development workflow; ONNX export is a single command.
- Model updates are zero-downtime via file-based hot-reload.
- Sidecar crashes do not take down the Scoring Service; fallback to rules is automatic.
- Clean resource isolation between JVM and native inference code.

**Negative / Risks:**
- ONNX export must be validated for each new model version (numerical equivalence test between Python XGBoost and ONNX Runtime outputs). This adds a step to the ML deployment pipeline.
- The file-watcher hot-reload mechanism requires careful implementation to avoid a brief window where both old and new model are in-flight for the same transaction. Atomic session swap + in-flight drain is implemented but adds complexity.
- ONNX format does not support all model types (e.g., custom ensemble structures may require custom operator registration). Currently not an issue for XGBoost; flagged as a risk if model architecture evolves significantly.

---

## Review Trigger

Revisit if:
- Model inference latency requirements tighten below 1ms p99 (would require JNI embedding despite isolation risks).
- The model type changes to one not well-supported by ONNX (e.g., a custom neural architecture with dynamic computation graphs).
- A managed inference platform (e.g., SageMaker Inference in-cluster, Vertex AI) becomes cost-effective and meets latency requirements.
