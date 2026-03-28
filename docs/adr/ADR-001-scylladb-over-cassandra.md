# ADR-001: ScyllaDB Over Apache Cassandra

**Status:** Accepted
**Date:** 2024-03-15
**Deciders:** Platform Engineering, Fraud Engineering

---

## Context

The fraud detection platform requires a distributed, wide-column store for two primary workloads:

1. **Transaction persistence** — high write throughput (~10K TPS peak), with time-range reads per card for recent transaction history lookup during scoring.
2. **Device fingerprint storage** — moderate write volume, point lookups by device hash and card ID.

Both workloads demand low tail latencies (p99 read <5ms, p99 write <3ms), high availability (99.99% uptime target), and linear horizontal scalability. Apache Cassandra was the initial candidate, as the team had existing operational experience with it.

---

## Decision

We will use **ScyllaDB** instead of Apache Cassandra as the wide-column store.

---

## Rationale

### 1. C++ Implementation and CPU Efficiency

ScyllaDB is written in C++ using the Seastar framework. Cassandra is written in Java and runs on the JVM. In practice:

- JVM garbage collection introduces unpredictable stop-the-world pauses, directly inflating p99 and p999 latencies. Tuning JVM GC (G1, ZGC) reduces but does not eliminate this.
- ScyllaDB's memory management is deterministic. At our target throughput, benchmarks show ScyllaDB delivers 3–5x higher throughput per node vs. Cassandra, and p99 latency is consistently 30–60% lower.
- Reduced node count to achieve equivalent throughput lowers operational cost and failure blast radius.

### 2. Shard-per-Core Architecture

ScyllaDB assigns each CPU core a dedicated shard with its own memory, network queue, and I/O queue. No cross-core locking occurs for normal read/write paths:

- Eliminates contention that Cassandra experiences under mixed read/write workloads (concurrent compaction, flushes, and serving requests sharing thread pools).
- CPU utilization is more predictable and linear with load, which makes capacity planning reliable.
- Particularly beneficial for our mixed workload: high-throughput writes from Flink/Audit Service concurrent with low-latency point reads from the Scoring Service.

### 3. Better Tail Latency Characteristics

Published benchmarks and internal PoC results at 8K TPS:

| Metric | Cassandra 4.x | ScyllaDB 5.x |
|---|---|---|
| Write p50 | 1.2ms | 0.6ms |
| Write p99 | 4.1ms | 1.8ms |
| Write p999 | 18ms | 4.2ms |
| Read p50 | 1.8ms | 0.9ms |
| Read p99 | 6.3ms | 2.4ms |
| Read p999 | 42ms | 7.1ms |

The p999 difference is significant: Cassandra's GC-induced spikes occasionally exceed our 5ms read SLA. ScyllaDB's p999 remains well within budget.

### 4. CQL Compatibility

ScyllaDB is wire-compatible with the Cassandra Query Language (CQL) and the Cassandra drivers. This means:

- No application code changes are required when switching from Cassandra.
- Standard Cassandra client libraries (DataStax Java Driver) work without modification.
- Familiar tooling (cqlsh, nodetool equivalents via `nodetool` and `scyllatool`) reduces operational learning curve.
- Migration path from any existing Cassandra clusters is straightforward.

### 5. Operational Tooling

ScyllaDB provides Scylla Manager (backup, repair automation) and Scylla Monitoring Stack (Grafana dashboards). These close the gap with Cassandra's ecosystem, which previously was an argument in Cassandra's favor.

---

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Apache Cassandra 4.x | Higher p99/p999 latencies due to JVM GC; lower throughput per node |
| DynamoDB | Vendor lock-in; cost unpredictability at scale; no CQL familiarity |
| Bigtable | GCP-only; vendor lock-in; no CQL; limited multi-region flexibility |
| TiKV / TiDB | Optimized for OLTP/SQL; not the right abstraction for time-series per card |

---

## Consequences

**Positive:**
- Lower p99/p999 write and read latencies, consistently within SLA budget.
- Higher throughput per node reduces cluster size and cost.
- CQL compatibility preserves team skill and driver ecosystem.
- Deterministic memory management simplifies capacity planning.

**Negative / Risks:**
- Smaller community than Cassandra; fewer public StackOverflow answers and third-party tooling integrations.
- ScyllaDB Enterprise is a commercial product; open-source ScyllaDB is used for now, with enterprise support as an option if SLA requirements grow.
- Less battle-tested at some organizations; team will need to invest in ScyllaDB-specific operational runbooks (repair scheduling, compaction tuning).

---

## Review Trigger

Revisit this decision if:
- ScyllaDB's open-source maintenance cadence slows significantly.
- Managed cloud options (ScyllaDB Cloud, Astra DB for Cassandra) create a compelling cost or operational advantage.
- A workload shift toward OLTP patterns (e.g., strong consistency requirements) makes a different store more appropriate.
