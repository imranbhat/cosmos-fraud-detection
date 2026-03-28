# ADR-002: Dual-Path Synchronous and Asynchronous Scoring

**Status:** Accepted
**Date:** 2024-03-22
**Deciders:** Platform Engineering, Fraud Engineering, Payments Engineering

---

## Context

Fraud scoring must serve two fundamentally different latency and consistency requirements:

1. **Real-time card authorization** — the card network expects a response within the authorization window (~100ms end-to-end; our internal SLA is <50ms for the scoring leg). A fraud decision must be returned synchronously as part of the authorization response.

2. **Batch review and enrichment** — risk reviews for flagged accounts, model training data pipelines, post-auth enrichment, and dispute investigation do not require synchronous responses and can tolerate seconds-to-minutes latency.

A purely synchronous architecture (e.g., direct gRPC calls from ingestion to scoring) risks cascading failures and adds coupling between services. A purely asynchronous architecture cannot meet the real-time authorization SLA without complex polling or callback mechanisms.

---

## Decision

Implement a **dual-path scoring architecture**:

- **Synchronous path** — Kafka request-reply pattern with a strict timeout, used for real-time card authorization.
- **Asynchronous path** — Standard Kafka consumer model, used for batch review, enrichment, and analytics.

Both paths share the same Kafka topics and the same scoring logic within the Scoring Service; they differ only in how callers wait for and consume results.

---

## Synchronous Path Design (Kafka Request-Reply)

```
Client → Ingestion Service
    → Publish to transactions.raw
        (headers: X-Correlation-Id, X-Reply-Partition)
    → Flink enriches → transactions.enriched
    → Scoring Service consumes, scores
    → Publishes to fraud.decisions (with X-Correlation-Id echoed)
    → API Gateway polls fraud.decisions filtered by X-Correlation-Id
        with a 40ms timeout
    → Returns decision in original HTTP response
```

**Why Kafka request-reply over direct gRPC:**

- Kafka provides durable in-flight message storage. If the Scoring Service restarts mid-flight, the message is not lost — it is reprocessed on consumer restart.
- Service topology remains decoupled. The Ingestion Service does not need to know the Scoring Service's address.
- The same `fraud.decisions` topic is the source of truth for all downstream consumers (Audit Service, ClickHouse, Alert Service). Decisions made on the synchronous path are automatically available asynchronously.
- Circuit breaker and fallback behaviour is cleanly separated: if the synchronous poll times out, the Ingestion Service returns a rule-only fallback decision without involving downstream consumers.

**Timeout handling:**

If no decision arrives in `fraud.decisions` within 40ms:
1. The API Gateway triggers the fallback scoring path (rule engine only, no ML model call).
2. A `fallback: true` flag is set in the response.
3. The enrichment and full scoring result are still published to `fraud.decisions` asynchronously, enabling post-auth review.
4. An alert is emitted to the `fraud.alerts` topic if the fallback score exceeds the MEDIUM threshold.

**Latency budget breakdown (synchronous path):**

| Step | Budget |
|---|---|
| API Gateway overhead | 2ms |
| Ingestion validation + publish | 5ms |
| Kafka broker round-trip (raw → enriched) | 3ms |
| Flink enrichment (including Redis read) | 10ms |
| Kafka broker round-trip (enriched → decisions) | 3ms |
| Scoring Service (rules + ML inference) | 22ms |
| API Gateway poll + response serialization | 5ms |
| **Total** | **50ms** |

---

## Asynchronous Path Design

The asynchronous path uses standard Kafka consumer group semantics:

```
fraud.decisions → [Consumer Group: audit-writer]    → ScyllaDB
               → [Consumer Group: clickhouse-ingest] → ClickHouse
               → [Consumer Group: alert-dispatcher]  → Alert Service
               → [Consumer Group: case-management]   → Review Queue
```

Batch review jobs consume directly from `transactions.raw` or `transactions.enriched` using separate consumer groups with higher `max.poll.records` and no latency SLA.

Model training pipelines read from ClickHouse, not from Kafka, to avoid impacting production consumer group lag.

---

## Alternatives Considered

### Option A: Synchronous gRPC Only

Direct gRPC call chain: Ingestion → Flink → Scoring → Response.

**Rejected because:**
- Tight coupling between services; scoring service address must be known at deployment.
- No durable in-flight storage; a scoring service restart drops the transaction.
- Backpressure propagates directly to the authorization endpoint, risking availability.
- Decisions are not automatically published to downstream consumers; a separate publish step would be needed anyway.

### Option B: Asynchronous Only with Client Polling

Return a `202 Accepted` with a `resultUrl`, and have the card network poll for the decision.

**Rejected because:**
- Card networks do not support asynchronous authorization flows. The authorization protocol is synchronous.
- Adds complexity for callers; polling logic must handle timeouts and partial results.
- Does not eliminate the need for a synchronous response to the card network.

### Option C: Separate Synchronous and Asynchronous Scoring Services

Deploy two distinct scoring deployments: one optimized for low-latency sync, one for throughput-oriented async.

**Rejected because:**
- Duplicates rule engine and model serving logic; two code paths to maintain and keep in sync.
- Model versions must be deployed to both services simultaneously.
- Operational complexity without meaningful benefit — the Scoring Service can handle both patterns at the volumes projected.

---

## Consequences

**Positive:**
- Real-time authorization SLA (<50ms) is achievable with the synchronous Kafka request-reply path.
- Decoupled architecture prevents scoring service failures from directly impacting authorization availability (fallback path).
- Single `fraud.decisions` topic serves as the source of truth for all downstream workloads.
- No separate infrastructure for async path — same topics, same service.

**Negative / Risks:**
- Kafka request-reply adds implementation complexity vs. direct gRPC. Correlation ID lifecycle management and partition assignment must be carefully implemented.
- Polling `fraud.decisions` with a 40ms timeout requires the API Gateway to maintain open connections, increasing connection count under high load. Connection pooling and tuning of Kafka `fetch.max.wait.ms` mitigates this.
- End-to-end latency is sensitive to Kafka broker performance. Broker saturation directly impacts authorization latency. Monitoring broker p99 produce/fetch latency is a first-class operational concern.

---

## Review Trigger

Revisit if:
- Card network authorization SLA tightens below 30ms, making the Kafka round-trips infeasible.
- A managed request-reply framework (e.g., a purpose-built RPC overlay on Kafka) becomes available and reduces implementation complexity.
- Transaction volume exceeds 50K TPS sustained, at which point broker saturation risk requires re-evaluation.
