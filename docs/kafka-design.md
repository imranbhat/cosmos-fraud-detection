# Kafka Topic Design

## Overview

Kafka serves as the event streaming backbone for all inter-service communication in the fraud detection pipeline. Topics are designed around the data flow stages: raw ingestion, enrichment, feature updates, decisioning, and alerting. All topics follow a consistent partitioning strategy and include a corresponding dead-letter queue (DLQ).

---

## Topic Inventory

| Topic | Partitions | Retention | Compacted | Key | Purpose |
|---|---|---|---|---|---|
| `transactions.raw` | 64 | 7 days | No | `cardId` | Raw validated transactions from Ingestion Service |
| `transactions.enriched` | 64 | 7 days | No | `cardId` | Flink-enriched transactions with feature vectors |
| `features.updates` | 32 | 3 days | Yes | `cardId` | Compacted feature snapshots for replay/bootstrap |
| `fraud.decisions` | 64 | 30 days | No | `cardId` | Scoring decisions with risk score and recommended action |
| `fraud.alerts` | 16 | 90 days | No | `cardId` | High-confidence fraud alerts routed to case management |
| `transactions.raw.dlq` | 16 | 14 days | No | `cardId` | Failed raw transaction processing |
| `transactions.enriched.dlq` | 16 | 14 days | No | `cardId` | Failed enrichment jobs |
| `features.updates.dlq` | 8 | 7 days | No | `cardId` | Failed feature update writes |
| `fraud.decisions.dlq` | 16 | 14 days | No | `cardId` | Failed decision publishing |
| `fraud.alerts.dlq` | 8 | 14 days | No | `cardId` | Failed alert delivery |

---

## Topic Specifications

### `transactions.raw`

```
Partitions:         64
Replication Factor: 3
Retention:          7 days (604800000 ms)
Cleanup Policy:     delete
Partition Key:      cardId (hash-based)
Producer ACKs:      all (acks=all)
Min ISR:            2
Compression:        lz4
Max Message Size:   1 MB
```

**Schema (Avro):**
```json
{
  "transactionId": "string (UUID)",
  "cardId": "string",
  "merchantId": "string",
  "merchantCategory": "string (MCC)",
  "amount": "decimal",
  "currency": "string (ISO 4217)",
  "country": "string (ISO 3166-1 alpha-2)",
  "deviceFingerprint": "string",
  "ipAddress": "string",
  "timestamp": "long (epoch ms)",
  "channel": "enum [CARD_PRESENT, CNP_WEB, CNP_APP, RECURRING]",
  "rawPayload": "bytes"
}
```

---

### `transactions.enriched`

```
Partitions:         64
Replication Factor: 3
Retention:          7 days
Cleanup Policy:     delete
Partition Key:      cardId
Producer ACKs:      all
Min ISR:            2
Compression:        lz4
```

**Schema additions over raw:**
```json
{
  "featureVector": {
    "txCount1h": "int",
    "txCount24h": "int",
    "avgAmount7d": "decimal",
    "lastCountry": "string",
    "velocityScore": "float",
    "deviceRiskScore": "float",
    "geoAnomalyScore": "float",
    "hourOfDay": "int",
    "dayOfWeek": "int",
    "isNewMerchant": "boolean",
    "isNewDevice": "boolean"
  },
  "enrichmentVersion": "string",
  "enrichmentTimestampMs": "long"
}
```

---

### `features.updates`

```
Partitions:         32
Replication Factor: 3
Retention:          3 days
Cleanup Policy:     compact (log compaction enabled)
Partition Key:      cardId
Min Compaction Lag: 1 hour
Segment Size:       512 MB
```

Used by Redis warm-up jobs and for disaster recovery bootstrap of the Feature Store. The compacted log represents the latest known feature state per `cardId`.

---

### `fraud.decisions`

```
Partitions:         64
Replication Factor: 3
Retention:          30 days
Cleanup Policy:     delete
Partition Key:      cardId
Producer ACKs:      all
Min ISR:            2
```

**Schema:**
```json
{
  "transactionId": "string",
  "cardId": "string",
  "riskScore": "float (0.0–1.0)",
  "riskBand": "enum [LOW, MEDIUM, HIGH, CRITICAL]",
  "recommendedAction": "enum [APPROVE, DECLINE, CHALLENGE_3DS, REVIEW]",
  "rulesTriggered": ["string"],
  "modelVersion": "string",
  "scoringLatencyMs": "int",
  "decidedAt": "long (epoch ms)"
}
```

---

### `fraud.alerts`

```
Partitions:         16
Replication Factor: 3
Retention:          90 days
Cleanup Policy:     delete
Partition Key:      cardId
```

Published when `riskBand = CRITICAL` or when rule-engine escalation logic fires. Consumed by case management and notification services.

---

## Dead-Letter Queue (DLQ) Strategy

Every topic has a corresponding `.dlq` topic. Consumers that fail to process a message after a configurable number of retries (default: 3, with exponential backoff) publish the original message to the DLQ with additional metadata headers:

```
X-DLQ-OriginalTopic:     string
X-DLQ-ConsumerGroup:     string
X-DLQ-FailureReason:     string
X-DLQ-AttemptCount:      int
X-DLQ-FirstFailureMs:    long
X-DLQ-LastFailureMs:     long
X-DLQ-ExceptionClass:    string
```

DLQ topics are monitored via alerting rules. An on-call runbook covers reprocessing procedures. Messages can be replayed to the original topic after the underlying issue is resolved.

---

## Partitioning Strategy

**Key:** All topics use `cardId` as the partition key. This ensures:

1. All events for a given card are processed in order by a single Flink task.
2. Velocity and behavioral feature state remains co-located.
3. Scoring decisions for a card are always routed to the same partition, simplifying request-reply correlation.

**Partition count rationale:** 64 partitions supports up to 64 parallel consumers per group. At 10K TPS peak with ~150-byte messages, each partition handles ~156 TPS — well within Kafka's per-partition throughput limits.

---

## Consumer Groups

| Consumer Group | Topic(s) Consumed | Service | Notes |
|---|---|---|---|
| `flink-enrichment` | `transactions.raw` | Flink Processor | Checkpoint-based offset management |
| `scoring-realtime` | `transactions.enriched` | Scoring Service | Request-reply pattern; short poll timeout |
| `audit-writer` | `fraud.decisions` | Audit Service | At-least-once with idempotent writes to ScyllaDB |
| `alert-dispatcher` | `fraud.alerts` | Alert Service | Fan-out to case management + push notifications |
| `clickhouse-ingest` | `transactions.enriched`, `fraud.decisions` | ClickHouse Kafka Engine | Batch insert, 1s poll |
| `feature-bootstrap` | `features.updates` | Redis Bootstrap Job | Run on startup or after Redis failure |

---

## Exactly-Once Semantics

The Flink pipeline uses Flink's built-in Kafka source/sink with checkpoint-based exactly-once delivery:

- **Source:** Flink Kafka source with checkpoint offsets committed on successful checkpoint.
- **Sink:** Flink Kafka sink with `EXACTLY_ONCE` semantic (`transactional.id` prefix configured per operator).
- **Checkpoint interval:** 5 seconds.
- **Transaction timeout:** 60 seconds (must exceed checkpoint interval + processing SLA).

The Scoring Service uses idempotent producers (`enable.idempotence=true`) for `fraud.decisions`. Downstream consumers that write to ScyllaDB and ClickHouse use idempotent upsert logic keyed on `transactionId` to tolerate at-least-once redelivery.

---

## Request-Reply Pattern (Synchronous Scoring)

For real-time authorization, the Scoring Service implements Kafka request-reply to achieve synchronous semantics over an async broker:

```
1. Ingestion Service publishes to transactions.raw with header:
     X-Correlation-Id: <UUID>
     X-Reply-Topic:    fraud.decisions
     X-Reply-Partition: <assigned partition>

2. Flink enriches and publishes to transactions.enriched (same headers propagated).

3. Scoring Service consumes, scores, publishes to fraud.decisions
   with X-Correlation-Id echoed in header.

4. API Gateway (or Ingestion Service) polls fraud.decisions filtered
   by X-Correlation-Id with a 40ms timeout.

5. On timeout, a cached rule-only fallback decision is returned.
```

This keeps the Kafka path as the system of record while meeting the sub-50ms authorization SLA.

---

## Configuration Reference

```properties
# Broker defaults for all topics
default.replication.factor=3
min.insync.replicas=2
unclean.leader.election.enable=false

# Producer defaults (all services)
acks=all
enable.idempotence=true
max.in.flight.requests.per.connection=5
compression.type=lz4
linger.ms=5
batch.size=65536

# Consumer defaults
isolation.level=read_committed
auto.offset.reset=earliest
enable.auto.commit=false
max.poll.records=500
```
