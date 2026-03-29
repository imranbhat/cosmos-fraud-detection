# Cosmos Fraud Detection - Product Requirements Document

## Overview

**cosmos-fraud-detection** is a production-grade, real-time fraud detection platform for a card-issuing bank. It processes card transactions in real time, computes risk scores using a combination of rule-based and ML-based engines, and supports EMV 3D Secure 2.x challenge flows.

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 25 (LTS) |
| Framework | Spring Boot | 4.0.x |
| Build Tool | Maven | 3.9+ |
| Streaming | Apache Kafka | 4.1+ |
| Stream Processing | Apache Flink | 2.2 |
| Feature Store | Redis (Lettuce) | 7.x |
| History DB | ScyllaDB | 6.x |
| Analytics DB | ClickHouse | 25.x |
| Serialization | Apache Avro | 1.12+ |
| Resilience | Resilience4j | 2.x |
| Observability | OpenTelemetry + Micrometer + LGTM Stack | - |
| ML Inference | ONNX Runtime | 1.22+ |
| Containerization | Docker + Kubernetes + Helm | - |

## Architecture

```
Transaction → API Gateway → Ingestion Service → Kafka(transactions.raw)
  → Flink(enrich + compute features) → Kafka(transactions.enriched)
  → Scoring Service(rules + ML) → Kafka(fraud.decisions) → Response

Side paths:
  - Redis: feature store read/write
  - ScyllaDB: transaction history (immutable audit trail)
  - ClickHouse: analytics-optimized storage
  - Audit Service: consumes all decision topics
```

## Project Structure

```
cosmos-fraud-detection/
├── pom.xml                             # Parent POM
├── gateway/                            # Spring Cloud Gateway
├── ingestion-service/                  # Transaction ingestion → Kafka producer
├── stream-processor/                   # Flink jobs: feature computation, enrichment
├── feature-store/                      # Redis feature read/write service
├── scoring-service/                    # Rule engine + ML inference, risk decisioning
├── threeds-service/                    # 3D Secure ACS integration (EMV 3DS 2.x)
├── audit-service/                      # Event consumer → ScyllaDB + ClickHouse
├── model-serving/                      # ML model serving (ONNX Runtime sidecar)
├── sdk-js/                             # JavaScript SDK (device fingerprinting)
├── sdk-react-native/                   # React Native SDK (device + behavioral)
├── common/                             # Shared DTOs, Avro schemas, utils, security
├── deployment/                         # K8s manifests, Helm charts
├── docs/                               # Architecture diagrams, ADRs, OpenAPI specs
└── docker/                             # Docker Compose for local dev stack
```

## Module Requirements

### Parent POM

- Java 25 (LTS)
- spring-boot-starter-parent 4.0.x as parent
- `<dependencyManagement>` for: Kafka, Flink, Redis (Lettuce), ScyllaDB driver, ClickHouse JDBC, Resilience4j, OpenTelemetry, ONNX Runtime, Avro
- `<pluginManagement>` for: maven-compiler-plugin, maven-surefire-plugin, maven-failsafe-plugin, spring-boot-maven-plugin, avro-maven-plugin, jib-maven-plugin
- Profiles: `local` (default, Testcontainers), `integration-test` (failsafe), `docker` (jib build)

### common/

- Avro schema compilation (avro-maven-plugin)
- Shared DTOs, error codes, security utils
- JWT validation filter (Spring Security)
- Kafka serializer/deserializer configs
- Common exception hierarchy (FraudPlatformException, ScoringTimeoutException, etc.)
- CorrelationIdFilter (servlet filter, propagates X-Correlation-ID via MDC)

**Avro Schemas:**
- `TransactionEvent.avsc` — txId, cardId, merchantId, amount, currency, mcc, channel[POS|ECOM|ATM], location{lat,lon,country}, deviceFingerprint, timestamp
- `EnrichedTransaction.avsc` — extends TransactionEvent + computed features
- `FraudDecision.avsc` — txId, cardId, riskScore, decision[APPROVE|DECLINE|CHALLENGE], appliedRules[], modelScores{}, latencyMs, timestamp
- `DeviceFingerprint.avsc` — fingerprintId, cardId, browserHash, canvasHash, webglHash, screenRes, timezone, behavioralSignals{}

### ingestion-service/

- Spring Boot 4.0 + spring-kafka
- POST /v1/transactions/score endpoint
- Validates input, assigns txId (ULID), publishes to `transactions.raw`
- Sync path: waits for scoring result via Kafka request-reply (ReplyingKafkaTemplate) with 45ms timeout
- Circuit breaker (Resilience4j) fallback: approve with flag for async review
- Request/response logging with correlation ID

### feature-store/

- Spring Boot + spring-data-redis (Lettuce client)
- Lua scripts for atomic feature updates (increment velocity counters with TTL)
- API: `getFeatures(cardId)`, `updateFeatures(cardId, txEvent)`
- Feature computations:
  - tx_count_1h, tx_count_6h, tx_count_24h (sliding window via sorted sets)
  - avg_amount_7d (running average)
  - distinct_merchants_24h (HyperLogLog)
  - country_change (last N countries via list)
  - time_since_last_tx

### scoring-service/

- Spring Boot consuming from `transactions.enriched`
- Rule engine (Strategy pattern chain):
  - VelocityRule: >10 tx/hr → +30 risk
  - AmountAnomalyRule: >3σ from avg → +25 risk
  - GeoAnomalyRule: country change <1hr → +40 risk
  - MerchantCategoryRule: high-risk MCC → +15 risk
  - DeviceChangeRule: new device → +20 risk
  - TimeAnomalyRule: unusual hour → +10 risk
- ML inference: ONNX Runtime, fallback to rules-only if model unavailable
- Score aggregation: weighted (rules 40%, ML 60%), normalize to 0-1000
- Thresholds (configurable):
  - 0-300: APPROVE
  - 301-700: CHALLENGE (trigger 3DS step-up)
  - 701-1000: DECLINE
- Publishes FraudDecision to `fraud.decisions`

### threeds-service/

- POST /v1/threeds/risk-assessment — calls scoring, returns transStatus (Y/C/R)
- POST /v1/threeds/challenge-result — validates challenge, updates decision
- Risk-based authentication (RBA):
  - Low risk → frictionless (Y)
  - Medium risk → challenge (C)
  - High risk → reject (R)
- EMV 3DS 2.x message format compliance

### stream-processor/ (Flink)

- TransactionEnrichmentJob:
  - Source: KafkaSource(transactions.raw)
  - Async I/O to feature-store, compute new features, update feature-store
  - Sink: KafkaSink(transactions.enriched) + KafkaSink(features.updates)
- FraudAlertAggregationJob:
  - Source: KafkaSource(fraud.decisions)
  - Tumbling 5-min window, detect decline spikes per merchant/region
  - Publish to `fraud.alerts`
- RocksDB state backend, 30s checkpointing, exactly-once

### audit-service/

- Kafka consumer for `fraud.decisions`, `fraud.alerts`
- Writes to ScyllaDB (immutable audit trail) and ClickHouse (analytics)
- Batch inserts to ClickHouse (every 1s or 1000 records)
- Replay endpoint: POST /v1/audit/replay?from=&to= for model retraining

### gateway/

- Spring Cloud Gateway (reactive)
- Rate limiting (Redis-based)
- JWT validation
- Circuit breakers per route (Resilience4j)
- Correlation ID propagation

### model-serving/

- ONNX Runtime sidecar for ML model inference
- Model hot-reload capability
- Health check endpoint

## Kafka Topics

| Topic | Partitions | Retention | Key | Notes |
|-------|-----------|-----------|-----|-------|
| transactions.raw | 64 | 7d | cardId | Raw inbound transactions |
| transactions.enriched | 64 | 7d | cardId | After feature enrichment |
| features.updates | 32 | 3d | cardId | Compacted topic |
| fraud.decisions | 64 | 30d | cardId | Scoring results |
| fraud.alerts | 16 | 90d | - | Aggregated fraud alerts |
| *.dlq | varies | 30d | - | Dead letter queues |

## Client SDKs

### sdk-js/ (JavaScript SDK)

- Vanilla JS, <15KB gzipped, no framework deps
- Collectors: browser, screen, canvas fingerprint, WebGL, timezone, behavioral (mouse/touch/keystroke)
- API: `FraudSDK.init()`, `FraudSDK.collect()`, `FraudSDK.send()`, `FraudSDK.on()`
- Privacy: no PII, hash everything, respect DNT

### sdk-react-native/

- React Native SDK for bank mobile apps
- Collectors: device info, network, location, behavioral (accelerometer, touch pressure)
- API: `<FraudSDKProvider>`, `useFraudSDK()` hook
- >80% test coverage

## Non-Functional Requirements

### Performance
- Sync scoring path: <50ms P99 latency
- Transaction throughput: >10,000 TPS
- Feature store reads: <5ms P99

### Observability
- LGTM stack: Loki (logs), Grafana (dashboards), Tempo (traces), Mimir (metrics)
- OpenTelemetry Collector as central telemetry pipeline (OTLP receivers → LGTM backends)
- Micrometer Tracing bridge → OTel OTLP exporter for distributed tracing
- Micrometer metrics (Prometheus format) scraped by OTel Collector → Mimir
- Structured JSON logging (logstash-logback-encoder) → Loki
- Grafana dashboards: TPS, scoring latency, decision distribution, Kafka consumer lag
- Full correlation: traces ↔ logs ↔ metrics (exemplars, derived fields, service graphs)

### Security
- JWT authentication at gateway
- PCI-DSS network policies in Kubernetes
- No hardcoded secrets (ExternalSecret CRDs)
- Masked card numbers in logs (last 4 only)

### Deployment
- Docker Compose for local dev (Kafka KRaft, Redis Cluster, ScyllaDB, ClickHouse)
- Helm chart for Kubernetes
- HPA: scoring-service on CPU (60%) + kafka_consumer_lag, min 3 / max 50 pods
- CI: Maven verify → jib build → Helm lint → Trivy scan

## Build Commands

```bash
mvn clean verify                              # Full build + tests
mvn -pl scoring-service test                  # Single module tests
mvn verify -P integration-test                # Integration tests
mvn jib:dockerBuild -P docker                 # Docker images
docker compose -f docker/docker-compose.yml up -d  # Local dev stack
```

## Implementation Phases

| Phase | Focus | Deliverables |
|-------|-------|-------------|
| 0 | Bootstrap | Project structure, POMs, docs, Avro schemas |
| 1 | Core Services | common, ingestion, feature-store, scoring |
| 2 | Remaining Services | 3DS, Flink, audit, gateway |
| 3 | Client SDKs | JS SDK, React Native SDK |
| 4 | Deployment | Docker, Helm, observability, CI |
