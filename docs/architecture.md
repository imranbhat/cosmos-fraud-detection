# System Architecture

## Overview

Cosmos Fraud Detection is a real-time transaction scoring platform designed to assess fraud risk during card authorization flows. The system processes transactions inline with the authorization path, targeting sub-50ms end-to-end latency for synchronous decisions, while supporting asynchronous enrichment and analytics workloads in parallel.

---

## C4 Context Diagram

```mermaid
C4Context
    title System Context — Cosmos Fraud Detection

    Person(cardholder, "Cardholder", "Initiates card-present or card-not-present transactions")
    Person(merchant, "Merchant", "Accepts card payments at POS or online checkout")
    System_Ext(cardNetwork, "Card Network", "Visa / Mastercard authorization network")
    System_Ext(issuerBank, "Issuer Bank", "Card-issuing bank that approves or declines")
    System_Ext(threeDSServer, "3DS Directory Server", "EMV 3-D Secure directory server")

    System_Boundary(cosmos, "Cosmos Fraud Detection") {
        System(fraudPlatform, "Fraud Detection Platform", "Real-time transaction scoring, 3DS risk assessment, device intelligence, and audit logging")
    }

    Rel(cardholder, merchant, "Pays with card")
    Rel(merchant, cardNetwork, "Submits authorization request")
    Rel(cardNetwork, fraudPlatform, "Requests fraud score", "HTTPS / ISO 8583")
    Rel(fraudPlatform, cardNetwork, "Returns risk decision + 3DS action")
    Rel(cardNetwork, issuerBank, "Forwards authorization with risk data")
    Rel(threeDSServer, fraudPlatform, "Sends ARes / CRes callbacks")
    Rel(fraudPlatform, threeDSServer, "Returns ARes risk indicators")
```

---

## C4 Container Diagram

```mermaid
C4Container
    title Container Diagram — Cosmos Fraud Detection

    Person_Ext(cardNetwork, "Card Network", "Upstream authorization network")

    System_Boundary(cosmos, "Cosmos Fraud Detection") {

        Container(apiGateway, "API Gateway", "Kong / Envoy", "TLS termination, auth, rate limiting, request routing")

        Container(ingestionSvc, "Ingestion Service", "Java / Spring Boot", "Validates, normalizes, and publishes raw transactions to Kafka")

        Container(flinkProcessor, "Flink Stream Processor", "Apache Flink", "Stateful stream enrichment: velocity checks, feature aggregation, geo-anomaly detection")

        Container(featureStore, "Feature Store", "Redis Cluster", "Low-latency read/write cache for real-time cardholder and device features")

        Container(scoringSvc, "Scoring Service", "Java / Spring Boot", "Orchestrates rule engine and ML model inference; returns risk score + action")

        Container(modelServing, "Model Serving Sidecar", "ONNX Runtime", "Hosts ONNX fraud detection model; hot-reload support; falls back to rules on failure")

        Container(threeDSSvc, "3DS Service", "Java / Spring Boot", "Handles EMV 3DS risk assessment and challenge result processing")

        Container(auditSvc, "Audit Service", "Java / Spring Boot", "Persists immutable decision audit trail; serves audit query API")

        ContainerDb(kafka, "Kafka Cluster", "Apache Kafka", "Event streaming backbone — see kafka-design.md for topic layout")

        ContainerDb(scyllaDB, "ScyllaDB", "ScyllaDB (CQL)", "Durable transaction storage and device fingerprint persistence")

        ContainerDb(clickhouse, "ClickHouse", "ClickHouse", "Analytical query engine for fraud reporting and model training data")
    }

    Rel(cardNetwork, apiGateway, "POST /v1/transactions/score", "HTTPS")
    Rel(apiGateway, ingestionSvc, "Routes inbound transaction", "HTTP/2")
    Rel(ingestionSvc, kafka, "Publishes to transactions.raw", "Kafka Producer")
    Rel(kafka, flinkProcessor, "Consumes transactions.raw", "Kafka Consumer")
    Rel(flinkProcessor, featureStore, "Reads/writes cardholder features", "Redis HGETALL / HSET")
    Rel(flinkProcessor, kafka, "Publishes to transactions.enriched", "Kafka Producer")
    Rel(kafka, scoringSvc, "Consumes transactions.enriched", "Kafka Consumer (request-reply)")
    Rel(scoringSvc, featureStore, "Reads live features", "Redis HGETALL")
    Rel(scoringSvc, modelServing, "gRPC InferenceRequest", "gRPC")
    Rel(scoringSvc, kafka, "Publishes to fraud.decisions", "Kafka Producer")
    Rel(kafka, auditSvc, "Consumes fraud.decisions", "Kafka Consumer")
    Rel(auditSvc, scyllaDB, "Writes audit records", "CQL")
    Rel(flinkProcessor, scyllaDB, "Persists enriched transactions", "CQL")
    Rel(flinkProcessor, clickhouse, "Streams analytics events", "Kafka → ClickHouse Kafka Engine")
    Rel(apiGateway, threeDSSvc, "POST /v1/threeds/*", "HTTP/2")
    Rel(kafka, threeDSSvc, "Consumes relevant decisions", "Kafka Consumer")
```

---

## Primary Data Flow

```mermaid
sequenceDiagram
    participant CN as Card Network
    participant GW as API Gateway
    participant IN as Ingestion Service
    participant K1 as Kafka<br/>transactions.raw
    participant FL as Flink Processor
    participant RS as Redis<br/>Feature Store
    participant K2 as Kafka<br/>transactions.enriched
    participant SC as Scoring Service
    participant ML as ONNX Model Sidecar
    participant K3 as Kafka<br/>fraud.decisions
    participant AU as Audit Service
    participant DB as ScyllaDB

    CN->>GW: POST /v1/transactions/score
    GW->>IN: Validated transaction payload
    IN->>K1: Publish raw transaction (key: cardId)
    K1->>FL: Consume raw transaction
    FL->>RS: Read cardholder features (HGETALL features:{cardId})
    FL->>RS: Update velocity counters (HINCRBY)
    FL->>K2: Publish enriched transaction (key: cardId)
    K2->>SC: Consume enriched transaction (request-reply)
    SC->>RS: Read latest features
    SC->>ML: gRPC InferenceRequest (feature vector)
    ML-->>SC: Risk score (0.0–1.0)
    SC->>K3: Publish fraud.decision (key: cardId)
    SC-->>GW: Risk score + recommended action
    GW-->>CN: HTTP 200 with decision payload
    K3->>AU: Consume decision
    AU->>DB: Persist immutable audit record
```

---

## Side Paths

### Feature Store Read/Write

```
Flink Processor ──HGETALL──► Redis features:{cardId}
                ◄──HSET/HINCRBY──
Scoring Service ──HGETALL──► Redis features:{cardId}
```

### Analytics Pipeline

```
Flink Processor ──► Kafka (transactions.enriched)
                         │
                         └──► ClickHouse Kafka Engine
                                    │
                                    └──► transactions_analytics (MergeTree)
```

### ScyllaDB Persistence

```
Flink Processor ──► ScyllaDB transactions table (partition: card_id)
Audit Service   ──► ScyllaDB audit_records table (partition: transaction_id)
```

---

## Component Responsibilities

| Component | Responsibility | SLA Target |
|---|---|---|
| API Gateway | AuthN/Z, rate limiting, TLS | <5ms overhead |
| Ingestion Service | Schema validation, normalization | <5ms p99 |
| Flink Processor | Feature aggregation, enrichment | <15ms p99 |
| Feature Store (Redis) | Sub-millisecond feature lookup | <1ms p99 |
| Scoring Service | Rule evaluation + ML inference coordination | <25ms p99 |
| ONNX Model Sidecar | Feature vector → risk score | <10ms p99 |
| 3DS Service | 3DS risk assessment, challenge handling | <30ms p99 |
| Audit Service | Immutable decision persistence | async, <500ms |
| ScyllaDB | Durable transaction + device storage | <5ms read p99 |
| ClickHouse | Analytical queries, model training export | batch / interactive |
