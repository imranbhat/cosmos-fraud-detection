# Cosmos Fraud Detection

Real-time fraud detection platform for a card-issuing bank.

## Tech Stack
- Java 21, Spring Boot 3.4, Apache Kafka, Apache Flink 1.19, Redis, ScyllaDB, ClickHouse
- Build: Maven multi-module
- ML: ONNX Runtime
- Serialization: Apache Avro

## Modules
- **common/** — Shared DTOs, Avro schemas, security, Kafka configs
- **ingestion-service/** — Transaction intake, Kafka producer, sync scoring path
- **feature-store/** — Redis-backed feature computation (velocity, geo, device)
- **scoring-service/** — Rule engine + ML inference, risk decisioning
- **threeds-service/** — EMV 3DS 2.x integration (frictionless/challenge/reject)
- **stream-processor/** — Flink jobs for enrichment and alert aggregation
- **audit-service/** — ScyllaDB + ClickHouse persistence, replay endpoint
- **gateway/** — Spring Cloud Gateway, rate limiting, JWT auth
- **model-serving/** — ONNX Runtime sidecar for ML models

## Build Commands
```bash
mvn clean verify                                    # Full build + unit tests
mvn -pl scoring-service test                        # Single module tests
mvn -pl ingestion-service spring-boot:run           # Run single service
mvn verify -P integration-test                      # Integration tests (Testcontainers)
mvn jib:dockerBuild -P docker                       # Build Docker images
docker compose -f docker/docker-compose.yml up -d   # Local dev stack
```

## Conventions
- Avro schemas in common/src/main/avro/, compiled via avro-maven-plugin
- Integration tests use *IT.java suffix, run with -P integration-test
- Kafka topics: transactions.raw, transactions.enriched, features.updates, fraud.decisions, fraud.alerts
- All services expose Prometheus metrics at /actuator/prometheus
- Structured JSON logging via logstash-logback-encoder
- Correlation ID propagated via X-Correlation-ID header + MDC
