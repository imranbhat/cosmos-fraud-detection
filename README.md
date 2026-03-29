# Cosmos Fraud Detection

A production-grade, real-time fraud detection platform for card-issuing banks. Processes card transactions in real time, computes risk scores using a combination of rule-based and ML-based engines, and supports EMV 3D Secure 2.x challenge flows.

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Modules](#modules)
- [Data Flow](#data-flow)
- [Kafka Topics](#kafka-topics)
- [Fraud Scoring](#fraud-scoring)
- [3D Secure Integration](#3d-secure-integration)
- [Client SDKs](#client-sdks)
- [Getting Started](#getting-started)
- [Build & Test](#build--test)
- [Local Development](#local-development)
- [Deployment](#deployment)
- [Observability](#observability)
- [API Reference](#api-reference)
- [Architecture Decision Records](#architecture-decision-records)
- [Contributing](#contributing)
- [License](#license)

## Architecture

```
                                    +------------------+
                                    |   Card Network   |
                                    +--------+---------+
                                             |
                              +--------------v--------------+
                              |        API Gateway          |
                              | (Spring Cloud Gateway)      |
                              | Rate Limiting / JWT / CORS  |
                              +--------------+--------------+
                                             |
                    +------------------------+------------------------+
                    |                                                 |
          +---------v----------+                          +-----------v-----------+
          | Ingestion Service  |                          |    3DS Service        |
          | POST /v1/txn/score |                          | Risk-Based Auth (RBA) |
          +---------+----------+                          | Challenge Flow        |
                    |                                     +-----------+-----------+
                    v                                                 |
          +-------------------+                                       |
          | Kafka             |<--------------------------------------+
          | transactions.raw  |
          +--------+----------+
                   |
          +--------v------------------+
          | Flink Stream Processor    |
          | - Feature Enrichment      |       +------------------+
          | - Async I/O to Redis     +------->| Feature Store    |
          | - Alert Aggregation       |       | (Redis Cluster)  |
          +--------+------------------+       +------------------+
                   |
          +--------v------------------+
          | Kafka                     |
          | transactions.enriched     |
          +--------+------------------+
                   |
          +--------v------------------+
          | Scoring Service           |
          | - 6 Fraud Rules           |       +------------------+
          | - ONNX ML Inference      +------->| Model Serving    |
          | - Weighted Aggregation    |       | (ONNX Runtime)   |
          +--------+------------------+       +------------------+
                   |
          +--------v------------------+
          | Kafka                     |
          | fraud.decisions           |
          +--------+------------------+
                   |
          +--------v------------------+
          | Audit Service             |
          | - ScyllaDB (audit trail) |
          | - ClickHouse (analytics) |
          +---------------------------+
```

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 (with preview features) |
| Framework | Spring Boot | 4.0.5 |
| Build Tool | Apache Maven | 3.9+ (multi-module) |
| Event Streaming | Apache Kafka | 4.1.2 (KRaft, no Zookeeper) |
| Stream Processing | Apache Flink | 2.2.0 |
| Feature Store | Redis | 7.x (Cluster mode, Lettuce client) |
| Audit Database | ScyllaDB | 6.x |
| Analytics Database | ClickHouse | 25.x |
| Serialization | Apache Avro | 1.12.1 |
| Schema Registry | Confluent | 8.1.2 |
| ML Inference | ONNX Runtime | 1.22.0 |
| Resilience | Resilience4j | 2.3.0 |
| Observability | OpenTelemetry + Micrometer + LGTM Stack | 1.51.0 |
| Containerization | Docker + Kubernetes + Helm | - |
| CI/CD | GitHub Actions | - |

## Project Structure

```
cosmos-fraud-detection/
├── pom.xml                        # Parent POM (Java 21, Spring Boot 4.0 BOM)
├── common/                        # Shared DTOs, Avro schemas, security, Kafka config
├── ingestion-service/             # Transaction intake, Kafka producer, sync scoring
├── feature-store/                 # Redis-backed feature computation (Lua scripts)
├── scoring-service/               # Rule engine + ML inference, risk decisioning
├── threeds-service/               # EMV 3D Secure 2.x (RBA + challenge flow)
├── stream-processor/              # Flink: enrichment + alert aggregation jobs
├── audit-service/                 # ScyllaDB + ClickHouse dual-write, replay
├── gateway/                       # Spring Cloud Gateway (rate limit, JWT, routing)
├── model-serving/                 # ONNX Runtime sidecar with hot-reload
├── sdk-js/                        # JavaScript SDK (device fingerprinting, <15KB)
├── sdk-react-native/              # React Native SDK (mobile fingerprinting)
├── deployment/                    # Helm chart (K8s manifests, Strimzi, monitoring)
├── docker/                        # Docker Compose (full local dev stack)
└── docs/                          # Architecture, API specs, ADRs, data models
```

## Modules

### common/
Shared foundation for all Java services:
- **Avro Schemas**: `TransactionEvent`, `EnrichedTransaction`, `FraudDecision`, `DeviceFingerprint`
- **DTOs**: `TransactionRequest`, `ScoringResponse`, `ErrorResponse` (Java 21 records)
- **Filters**: `CorrelationIdFilter` (MDC propagation), `JwtAuthenticationFilter`
- **Kafka**: `KafkaConfig` (Confluent Avro serializers), `KafkaTopics` constants
- **Utils**: `IdGenerator` (monotonic ULID for time-sortable transaction IDs)
- **Exceptions**: `FraudPlatformException`, `ScoringTimeoutException`, `ValidationException`

### ingestion-service/ (port 8081)
Transaction intake with synchronous scoring:
- `POST /v1/transactions/score` — validates, assigns ULID, publishes to Kafka
- **Sync path**: `ReplyingKafkaTemplate` with 45ms timeout for real-time auth decisions
- **Circuit breaker**: Resilience4j fallback approves with `ASYNC_REVIEW_REQUIRED` flag
- Correlation ID propagation via request/response headers

### feature-store/ (port 8082)
Redis-backed real-time feature computation:
- **Lua script** for atomic updates (single round-trip):
  - Sliding window tx counts (1h, 6h, 24h) via sorted sets
  - Running average amount (7-day)
  - Distinct merchants (HyperLogLog)
  - Country change detection
  - Composite velocity score
- `GET /v1/features/{cardId}` — retrieve computed features
- `POST /v1/features/{cardId}/update` — update features from transaction

### scoring-service/ (port 8083)
Fraud risk scoring with dual engine:
- **Rule Engine**: 6 rules via Strategy pattern (see [Fraud Scoring](#fraud-scoring))
- **ML Inference**: ONNX Runtime with graceful fallback to rules-only
- **Score Aggregation**: weighted fusion (40% rules, 60% ML), normalized 0-1000
- Kafka consumer on `transactions.enriched`, publishes to `fraud.decisions`
- Supports sync reply for request-reply pattern

### threeds-service/ (port 8084)
EMV 3D Secure 2.x integration:
- `POST /v1/threeds/risk-assessment` — risk-based authentication (Y/C/R)
- `POST /v1/threeds/challenge-result` — challenge outcome validation
- In-memory challenge session tracking with ECI assignment (05 Visa, 02 MC)

### stream-processor/ (Flink fat JAR)
Apache Flink 2.2 streaming jobs:
- **TransactionEnrichmentJob**: `transactions.raw` → async feature enrichment → `transactions.enriched`
- **FraudAlertAggregationJob**: `fraud.decisions` → 5-min tumbling window → decline spike detection → `fraud.alerts`
- RocksDB state backend, exactly-once checkpointing every 30s

### audit-service/ (port 8085)
Immutable audit trail and analytics:
- Kafka consumer for `fraud.decisions` and `fraud.alerts`
- **ScyllaDB**: immutable transaction + decision records (CQL prepared statements)
- **ClickHouse**: batch inserts (flush every 1s or 1000 records) for analytics
- `POST /v1/audit/replay` — republish historical data for model retraining

### gateway/ (port 8080)
API Gateway (Spring Cloud Gateway, reactive):
- Routes to all backend services with path-based predicates
- **Rate limiting**: Redis-backed (100 req/s, 200 burst capacity)
- **JWT validation**: OAuth2 resource server
- **Circuit breakers**: per-route via Resilience4j
- Correlation ID generation and propagation

### model-serving/ (port 8086)
ONNX Runtime sidecar:
- `POST /v1/model/predict` — feature array to fraud probability
- `POST /v1/model/reload` — hot-reload model without downtime
- `GET /v1/model/health` — model availability status
- Thread-safe inference with `ReadWriteLock`

## Data Flow

```
1. Transaction arrives at API Gateway (JWT validated, rate limited)
2. Ingestion Service validates input, generates ULID, publishes to Kafka
3. Flink reads from transactions.raw:
   a. Async I/O to Feature Store (Redis) for current features
   b. Computes new features, updates Redis atomically (Lua script)
   c. Publishes EnrichedTransaction to transactions.enriched
4. Scoring Service consumes enriched transaction:
   a. Runs 6 fraud rules (Strategy pattern)
   b. Runs ML inference (ONNX Runtime)
   c. Weighted fusion: 40% rules + 60% ML → 0-1000 score
   d. Decision: APPROVE (0-300), CHALLENGE (301-700), DECLINE (701-1000)
   e. Publishes FraudDecision to fraud.decisions
5. Sync path: reply sent back to Ingestion within 45ms
6. Audit Service persists to ScyllaDB (audit) + ClickHouse (analytics)
7. Flink alert job detects decline spikes → fraud.alerts
```

## Kafka Topics

| Topic | Partitions | Retention | Key | Purpose |
|-------|-----------|-----------|-----|---------|
| `transactions.raw` | 64 | 7 days | cardId | Raw inbound transactions |
| `transactions.enriched` | 64 | 7 days | cardId | Feature-enriched transactions |
| `features.updates` | 32 | 3 days | cardId | Compacted feature updates |
| `fraud.decisions` | 64 | 30 days | cardId | Scoring decisions |
| `fraud.alerts` | 16 | 90 days | — | Aggregated fraud alerts |
| `*.dlq` | varies | 30 days | — | Dead letter queues |

- **Partitioning**: card_id hash for ordering guarantees per cardholder
- **Replication**: factor 3, min ISR 2
- **Compression**: LZ4
- **Semantics**: exactly-once via Flink checkpointing + Kafka transactions

## Fraud Scoring

### Rule Engine (Strategy Pattern)

| Rule | Condition | Risk Score |
|------|-----------|-----------|
| **VelocityRule** | >10 tx/hr | +30 |
| **AmountAnomalyRule** | >3x average (7-day) | +25 |
| **GeoAnomalyRule** | Country change <1hr | +40 |
| **MerchantCategoryRule** | High-risk MCC (gambling, drugs) | +15 |
| **DeviceChangeRule** | New device fingerprint | +20 |
| **TimeAnomalyRule** | Transaction at 1-5 AM | +10 |

### ML Inference
- ONNX Runtime with 9-feature input vector
- Fallback to rules-only if model unavailable (circuit breaker)

### Score Aggregation
```
finalScore = (ruleScore * 0.4) + (mlProbability * 1000 * 0.6)
```
Clamped to 0-1000. Weights configurable via `application.yml`.

### Decision Thresholds (configurable)

| Score Range | Decision | Action |
|------------|----------|--------|
| 0 - 300 | APPROVE | Transaction proceeds |
| 301 - 700 | CHALLENGE | 3DS step-up authentication |
| 701 - 1000 | DECLINE | Transaction blocked |

## 3D Secure Integration

EMV 3DS 2.x risk-based authentication:

| Risk Level | transStatus | acsChallengeMandated | Flow |
|-----------|-------------|---------------------|------|
| Low (0-300) | Y | false | Frictionless — no user interaction |
| Medium (301-700) | C | true | Challenge — OTP/biometric required |
| High (701-1000) | R | false | Reject — transaction denied |

**ECI Values**: 05 (Visa frictionless/challenge), 02 (Mastercard)

## Client SDKs

### JavaScript SDK (`sdk-js/`)
Lightweight device fingerprinting (<15KB gzipped) for web:
```javascript
FraudSDK.init({ clientId: 'bank123', endpoint: 'https://api.bank.com/v1/devices/fingerprint' });
const result = await FraudSDK.collect();
const { fingerprintId } = await FraudSDK.send();
FraudSDK.on('error', (err) => console.error(err));
```

**Collectors**: browser info, screen resolution, canvas fingerprint (SHA-256), WebGL renderer hash, timezone, behavioral signals (mouse/touch/keystroke).

Privacy: no PII stored, all signals hashed, respects `navigator.doNotTrack`.

### React Native SDK (`sdk-react-native/`)
Mobile device fingerprinting for bank apps:
```tsx
<FraudSDKProvider clientId="bank123" endpoint="https://api.bank.com/v1/devices/fingerprint">
  <App />
</FraudSDKProvider>

// In component:
const { collect, send, fingerprintId, status } = useFraudSDK();
await collect();
const result = await send();
```

**Collectors**: device info, network state, GPS location (permission-gated), behavioral (accelerometer, touch pressure, typing cadence).

## Getting Started

### Prerequisites
- Java 21 (Temurin recommended)
- Maven 3.9+ (or use included wrapper after setup)
- Docker & Docker Compose (for local infrastructure)
- Node.js 20+ (for SDKs)

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/imranbhat/cosmos-fraud-detection.git
cd cosmos-fraud-detection

# 2. Start infrastructure (Kafka, Redis, ScyllaDB, ClickHouse, monitoring)
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml up -d

# 3. Build all Java modules
mvn clean verify

# 4. Run a specific service
mvn -pl ingestion-service spring-boot:run

# 5. Build and test SDKs
cd sdk-js && npm install && npm test && npm run build
cd ../sdk-react-native && npm install && npm test
```

## Build & Test

### Java Services

```bash
# Full build with unit tests
mvn clean verify

# Single module
mvn -pl scoring-service test

# Integration tests (requires Testcontainers / running infra)
mvn verify -P integration-test

# Build Docker images via Jib
mvn jib:dockerBuild -P docker

# Run a specific service
mvn -pl ingestion-service spring-boot:run
mvn -pl scoring-service spring-boot:run
mvn -pl feature-store spring-boot:run
```

### JavaScript SDK

```bash
cd sdk-js
npm install
npm test          # Jest tests
npm run build     # Rollup → UMD + ESM in dist/
npm run lint      # ESLint
```

### React Native SDK

```bash
cd sdk-react-native
npm install
npm test          # Jest tests
```

### Flink Jobs

```bash
# Build fat JAR for Flink submission
mvn -pl stream-processor package

# Submit to Flink cluster
flink run stream-processor/target/stream-processor-1.0.0-SNAPSHOT.jar \
  --kafka.bootstrap.servers localhost:9092 \
  --feature.store.url http://localhost:8082
```

## Local Development

### Docker Compose Stack

```bash
cp docker/.env.example docker/.env    # Configure environment
docker compose -f docker/docker-compose.yml up -d
```

This starts the full infrastructure:

| Service | Host Port | Purpose |
|---------|----------|---------|
| Kafka Broker 1 | 9092 | Event streaming |
| Kafka Broker 2 | 9093 | Event streaming |
| Kafka Broker 3 | 9094 | Event streaming |
| Schema Registry | 18081 | Avro schema management |
| Redis Cluster | 7001-7006 | Feature store |
| ScyllaDB | 9042-9044 | Audit trail |
| ClickHouse | 8123 / 9000 | Analytics (HTTP / native) |
| OTel Collector | 4317 / 4318 | OTLP gRPC / HTTP receiver |
| Grafana Tempo | 3200 | Distributed tracing (HTTP API) |
| Grafana Loki | 3100 | Log aggregation (HTTP API) |
| Grafana Mimir | 9009 | Metrics storage (Prometheus-compatible) |
| Grafana | 3000 | Dashboards (admin/admin) |

### Service Ports

| Service | Port |
|---------|------|
| Gateway | 8080 |
| Ingestion Service | 8081 |
| Feature Store | 8082 |
| Scoring Service | 8083 |
| 3DS Service | 8084 |
| Audit Service | 8085 |
| Model Serving | 8086 |

## Deployment

### Kubernetes (Helm)

```bash
# Lint the chart
helm lint deployment/

# Install (dev)
helm install cosmos-fraud deployment/ -n fraud-detection --create-namespace

# Install (production)
helm install cosmos-fraud deployment/ -n fraud-detection \
  -f deployment/values-prod.yaml \
  --create-namespace

# Upgrade
helm upgrade cosmos-fraud deployment/ -n fraud-detection
```

### Helm Chart Highlights

- **HPA**: Scoring service scales on CPU (60%) + Kafka consumer lag (10K threshold), min 3 / max 50 pods
- **PDB**: `maxUnavailable: 1` on scoring service
- **Network Policies**: PCI-DSS compliant default-deny with selective allow rules
- **Secrets**: ExternalSecret CRDs (no hardcoded values)
- **Infrastructure**: Strimzi Kafka CRD (KRaft), Redis Cluster StatefulSet, Flink Kubernetes Operator
- **Monitoring**: ServiceMonitor, 12 PrometheusRules, Grafana dashboard ConfigMap

### CI/CD (GitHub Actions)

The pipeline (`.github/workflows/ci.yml`) runs:

| Job | Trigger | Description |
|-----|---------|-------------|
| `build` | All pushes/PRs | `mvn clean verify` |
| `integration-test` | After build | `mvn verify -P integration-test` |
| `docker` | Main branch only | `mvn jib:build -P docker` |
| `helm-lint` | All pushes/PRs | `helm lint deployment/` |
| `security-scan` | Main branch only | Trivy filesystem scan |
| `sdk-js` | All pushes/PRs | `npm test && npm run build` |
| `sdk-react-native` | All pushes/PRs | `npm test` |

## Observability

The platform uses the **LGTM stack** (Loki, Grafana, Tempo, Mimir) with an **OpenTelemetry Collector** as the central telemetry pipeline.

```
Services → [OTLP] → OTel Collector → Tempo   (traces)
                                    → Loki    (logs)
                                    → Mimir   (metrics)
                                                ↓
                                            Grafana (unified dashboards)
```

### Metrics (Micrometer + OTel Collector → Mimir)
All services expose metrics at `/actuator/prometheus`, scraped by the OTel Collector and forwarded to Grafana Mimir:
- `fraud_scoring_latency_ms` — scoring pipeline latency
- `fraud_decisions_total{decision}` — decision counts by type
- `fraud_model_inference_latency_ms` — ML inference timing
- Standard Spring Boot / Kafka / Redis metrics
- Span-metrics generated by Tempo (RED metrics per service)

### Structured Logging (Logstash Logback → OTel Collector → Loki)
JSON output via `logstash-logback-encoder` with MDC fields:
- `correlationId` — request correlation across services
- `cardId` — masked (last 4 digits only)
- `txId` — ULID transaction identifier
- `service` — originating service name

Logs are sent to Loki via the OTel Collector. Grafana Loki derived fields extract `trace_id` for seamless log-to-trace correlation.

### Distributed Tracing (Micrometer Tracing → OTel → Tempo)
Micrometer Tracing bridge exports traces via OTLP to the OTel Collector, which forwards to Grafana Tempo. W3C `traceparent` header propagation across HTTP and Kafka messages. Tempo generates service graphs and span-metrics for RED dashboards.

### Grafana Dashboards
Pre-configured dashboards with full LGTM correlation:
- Transaction throughput (TPS) by service
- Scoring latency percentiles (P50, P95, P99)
- Fraud decision distribution (approve / challenge / decline)
- Kafka consumer lag per consumer group
- Error rate (5xx) by service
- Service topology graph (Tempo service graphs)
- Traces ↔ Logs ↔ Metrics cross-linking (click from trace to logs, metrics to exemplar traces)

### Prometheus Alert Rules (12 rules)
- High fraud rate (>10%)
- Scoring P99 latency > 100ms
- Kafka consumer lag > 10,000
- Kafka broker down
- 5xx error rate spike
- Pod crash looping
- Redis unavailability
- High memory usage

## API Reference

### Transaction Scoring
```
POST /v1/transactions/score
Content-Type: application/json

{
  "cardId": "4532XXXXXXXX1234",
  "merchantId": "MERCH_001",
  "amount": 299.99,
  "currency": "USD",
  "mcc": "5411",
  "channel": "ECOM",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "country": "US",
  "deviceFingerprint": "fp_abc123"
}

Response:
{
  "txId": "01HQ3V5JKWM3KQZX7V8N2DBCR4",
  "riskScore": 245,
  "decision": "APPROVE",
  "appliedRules": ["VELOCITY", "TIME_ANOMALY"],
  "latencyMs": 32
}
```

### 3DS Risk Assessment
```
POST /v1/threeds/risk-assessment
Content-Type: application/json

{
  "cardId": "4532XXXXXXXX1234",
  "merchantId": "MERCH_001",
  "amount": 1500.00,
  "currency": "USD",
  "messageVersion": "2.2.0",
  "deviceInfo": { "deviceId": "dev_001", "deviceType": "mobile", "os": "iOS", "osVersion": "17.4" },
  "browserInfo": { "userAgent": "...", "language": "en", "colorDepth": 24, "screenHeight": 1920, "screenWidth": 1080 }
}

Response:
{
  "threeDSServerTransID": "550e8400-e29b-41d4-a716-446655440000",
  "transStatus": "C",
  "acsChallengeMandated": true,
  "riskScore": 520,
  "messageVersion": "2.2.0"
}
```

### Feature Retrieval
```
GET /v1/features/{cardId}

Response:
{
  "cardId": "4532XXXXXXXX1234",
  "txCountOneHour": 3,
  "txCountTwentyFourHours": 12,
  "avgAmountSevenDays": 85.50,
  "distinctMerchantsTwentyFourHours": 4,
  "countryChanged": false,
  "velocityScore": 0.23
}
```

### Audit Trail
```
GET /v1/transactions/{txId}/audit

Response:
{
  "txId": "01HQ3V5JKWM3KQZX7V8N2DBCR4",
  "cardId": "4532XXXXXXXX1234",
  "riskScore": 245,
  "decision": "APPROVE",
  "appliedRules": ["VELOCITY", "TIME_ANOMALY"],
  "latencyMs": 32,
  "timestamp": "2024-03-15T10:30:00Z"
}
```

Full OpenAPI 3.1 specifications are in [`docs/api-specs/`](docs/api-specs/).

## Architecture Decision Records

| ADR | Decision | Rationale |
|-----|----------|-----------|
| [ADR-001](docs/adr/ADR-001-scylladb-over-cassandra.md) | ScyllaDB over Cassandra | C++ implementation, better tail latencies, shard-per-core |
| [ADR-002](docs/adr/ADR-002-sync-vs-async-scoring.md) | Dual sync/async scoring path | <50ms sync for real-time auth, async for batch/review |
| [ADR-003](docs/adr/ADR-003-rule-engine-choice.md) | Custom rules over Drools | Simpler, testable, no DSL overhead for ~20 rules |
| [ADR-004](docs/adr/ADR-004-ml-model-serving.md) | ONNX Runtime sidecar | Language-agnostic, low latency, hot-reload, rules fallback |

## Non-Functional Requirements

| Metric | Target |
|--------|--------|
| Sync scoring latency | < 50ms P99 |
| Transaction throughput | > 10,000 TPS |
| Feature store reads | < 5ms P99 |
| Availability | 99.99% |
| Scoring service scaling | 3-50 pods (HPA) |

## Project Stats

- **Java source files**: 79
- **Test files**: 27
- **Avro schemas**: 4
- **Kafka topics**: 5 (+5 DLQs)
- **Fraud rules**: 6
- **Helm templates**: 31
- **Prometheus alert rules**: 12
- **Docker Compose services**: 20+
- **OpenAPI specs**: 4
- **ADRs**: 4

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feat/my-feature`)
3. Write tests first (TDD approach)
4. Implement the feature
5. Run `mvn clean verify` to ensure all tests pass
6. Commit with conventional format (`feat:`, `fix:`, `test:`, `docs:`, etc.)
7. Open a Pull Request

## License

This project is proprietary software. All rights reserved.
