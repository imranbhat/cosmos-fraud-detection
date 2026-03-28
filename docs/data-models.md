# Data Models

## Overview

The platform uses three distinct data stores, each selected for a specific access pattern:

- **Redis** — sub-millisecond feature reads during live scoring
- **ScyllaDB** — durable, high-throughput transaction and device persistence
- **ClickHouse** — analytical queries and model training data export

---

## Redis — Feature Store

Redis is deployed as a cluster. All feature data is stored as hashes under a predictable key pattern to allow O(1) lookup during scoring.

### Key Structure

```
features:{cardId}          → Hash  (cardholder behavioral features)
device:{deviceHash}        → Hash  (device risk profile)
velocity:{cardId}:{window} → String (raw counter, used by Flink INCR)
```

### `features:{cardId}` Hash Fields

| Field | Type | Description | TTL |
|---|---|---|---|
| `tx_count_1h` | integer | Transaction count in the last 1 hour | 2 hours |
| `tx_count_24h` | integer | Transaction count in the last 24 hours | 48 hours |
| `tx_count_7d` | integer | Transaction count in the last 7 days | 14 days |
| `avg_amount_7d` | float | Rolling average transaction amount over 7 days | 14 days |
| `max_amount_7d` | float | Maximum single transaction amount over 7 days | 14 days |
| `last_country` | string (ISO 3166-1) | Country of the most recent transaction | 72 hours |
| `last_merchant_id` | string | Merchant ID of the most recent transaction | 72 hours |
| `device_hash` | string | SHA-256 of the most recently seen device fingerprint | 72 hours |
| `velocity_score` | float (0.0–1.0) | Computed velocity anomaly score | 2 hours |
| `geo_anomaly_score` | float (0.0–1.0) | Geographic anomaly score vs. home country | 2 hours |
| `new_device_flag` | boolean (0/1) | 1 if current device was first seen in last 24h | 24 hours |
| `updated_at` | long (epoch ms) | Last write timestamp | — |

**Example:**
```redis
HGETALL features:card_abc123
1) "tx_count_1h"        2) "3"
3) "tx_count_24h"       4) "12"
5) "tx_count_7d"        6) "47"
7) "avg_amount_7d"      8) "84.32"
9) "max_amount_7d"      10) "420.00"
11) "last_country"      12) "US"
13) "device_hash"       14) "d3a9f1..."
15) "velocity_score"    16) "0.12"
17) "geo_anomaly_score" 18) "0.05"
19) "new_device_flag"   20) "0"
21) "updated_at"        22) "1711613422000"
```

### `device:{deviceHash}` Hash Fields

| Field | Type | Description |
|---|---|---|
| `first_seen_at` | long (epoch ms) | Timestamp of first observed transaction |
| `last_seen_at` | long (epoch ms) | Timestamp of most recent transaction |
| `card_count` | integer | Number of distinct cards seen on this device |
| `tx_count_30d` | integer | Transactions in the last 30 days |
| `risk_score` | float (0.0–1.0) | Device-level risk score |
| `flagged` | boolean (0/1) | 1 if device has been associated with confirmed fraud |

### TTL Strategy

Hash-level TTL is set on the parent key. Flink refreshes the TTL on every write. Keys expire automatically if a card has no activity. There is no explicit eviction policy dependency — Redis is sized to hold the active cardholder population in memory.

---

## ScyllaDB

ScyllaDB stores durable transaction records and device fingerprint history. The schema is designed for high write throughput and efficient time-range reads per card.

### Keyspace

```cql
CREATE KEYSPACE IF NOT EXISTS fraud_detection
WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'dc1': 3
}
AND durable_writes = true;
```

### `transactions` Table

```cql
CREATE TABLE fraud_detection.transactions (
    card_id          TEXT,
    timestamp        TIMESTAMP,
    transaction_id   UUID,
    merchant_id      TEXT,
    merchant_category TEXT,
    amount           DECIMAL,
    currency         TEXT,
    country          TEXT,
    channel          TEXT,
    device_hash      TEXT,
    ip_address       TEXT,
    risk_score       FLOAT,
    risk_band        TEXT,
    decision         TEXT,
    rules_triggered  LIST<TEXT>,
    model_version    TEXT,
    enriched_features MAP<TEXT, TEXT>,
    created_at       TIMESTAMP,
    PRIMARY KEY ((card_id), timestamp, transaction_id)
) WITH CLUSTERING ORDER BY (timestamp DESC, transaction_id ASC)
  AND default_time_to_live = 7776000  -- 90 days
  AND compaction = {
      'class': 'TimeWindowCompactionStrategy',
      'compaction_window_unit': 'DAYS',
      'compaction_window_size': 1
  }
  AND bloom_filter_fp_chance = 0.01
  AND compression = {'sstable_compression': 'LZ4Compressor'};
```

**Access patterns:**
- Fetch last N transactions for a card: `WHERE card_id = ? ORDER BY timestamp DESC LIMIT N`
- Fetch transactions in a time window: `WHERE card_id = ? AND timestamp >= ? AND timestamp <= ?`
- Fetch single transaction by card + time: equality on partition + clustering column

**Secondary index (for audit lookups by `transaction_id`):**
```cql
CREATE INDEX ON fraud_detection.transactions (transaction_id);
```

### `device_fingerprints` Table

```cql
CREATE TABLE fraud_detection.device_fingerprints (
    device_hash      TEXT,
    card_id          TEXT,
    first_seen_at    TIMESTAMP,
    last_seen_at     TIMESTAMP,
    tx_count         COUNTER,
    PRIMARY KEY ((device_hash), card_id)
) WITH compaction = {'class': 'LeveledCompactionStrategy'}
  AND compression = {'sstable_compression': 'LZ4Compressor'};
```

**Access patterns:**
- Look up all cards associated with a device: `WHERE device_hash = ?`
- Determine if a device-card pairing is new: check row existence

### `audit_records` Table

```cql
CREATE TABLE fraud_detection.audit_records (
    transaction_id   UUID,
    event_type       TEXT,
    event_timestamp  TIMESTAMP,
    service_name     TEXT,
    payload          TEXT,   -- JSON blob
    PRIMARY KEY ((transaction_id), event_timestamp, event_type)
) WITH CLUSTERING ORDER BY (event_timestamp ASC, event_type ASC)
  AND default_time_to_live = 31536000  -- 1 year
  AND compaction = {'class': 'LeveledCompactionStrategy'};
```

Used exclusively by the Audit Service to record the full lifecycle of a transaction decision.

---

## ClickHouse

ClickHouse stores denormalized analytics events for fraud reporting, rule performance analysis, and model training data export.

### `transactions_analytics` Table

```sql
CREATE TABLE fraud_detection.transactions_analytics
(
    transaction_id      UUID,
    card_id             String,
    merchant_id         String,
    merchant_category   String,
    amount              Decimal(18, 4),
    currency            FixedString(3),
    country             FixedString(2),
    channel             LowCardinality(String),
    device_hash         String,
    ip_address          String,

    -- Enriched features
    tx_count_1h         UInt16,
    tx_count_24h        UInt16,
    avg_amount_7d       Float32,
    velocity_score      Float32,
    geo_anomaly_score   Float32,
    is_new_device       UInt8,

    -- Decision
    risk_score          Float32,
    risk_band           LowCardinality(String),
    decision            LowCardinality(String),
    rules_triggered     Array(String),
    model_version       String,
    scoring_latency_ms  UInt16,

    -- Outcome (populated async after review/chargeback)
    is_confirmed_fraud  Nullable(UInt8),
    chargeback_at       Nullable(DateTime),
    reviewed_by         Nullable(String),

    -- Timestamps
    timestamp           DateTime,
    date                Date MATERIALIZED toDate(timestamp)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (card_id, timestamp, transaction_id)
TTL date + INTERVAL 24 MONTH
SETTINGS index_granularity = 8192;
```

### Materialized Views

**Hourly velocity summary (for rule calibration):**
```sql
CREATE MATERIALIZED VIEW fraud_detection.velocity_summary_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (card_id, hour)
AS SELECT
    card_id,
    toStartOfHour(timestamp) AS hour,
    count()                  AS tx_count,
    sum(amount)              AS total_amount,
    sum(is_confirmed_fraud)  AS fraud_count
FROM fraud_detection.transactions_analytics
GROUP BY card_id, hour;
```

**Decision distribution by risk band:**
```sql
CREATE MATERIALIZED VIEW fraud_detection.decision_distribution_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, risk_band, decision)
AS SELECT
    date,
    risk_band,
    decision,
    count()   AS tx_count,
    sum(amount) AS total_amount
FROM fraud_detection.transactions_analytics
GROUP BY date, risk_band, decision;
```

### Ingestion

ClickHouse consumes from Kafka using the ClickHouse Kafka table engine:

```sql
CREATE TABLE fraud_detection.transactions_analytics_kafka_queue
(
    -- same columns as transactions_analytics minus computed columns
)
ENGINE = Kafka()
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list  = 'transactions.enriched,fraud.decisions',
    kafka_group_name  = 'clickhouse-ingest',
    kafka_format      = 'Avro',
    kafka_num_consumers = 4;
```

A materialized view joins and routes rows from the Kafka queue into `transactions_analytics`.

---

## Data Retention Summary

| Store | Dataset | Retention |
|---|---|---|
| Redis | `features:{cardId}` | 2–48 hours (per field TTL) |
| Redis | `device:{deviceHash}` | 30 days |
| ScyllaDB | `transactions` | 90 days |
| ScyllaDB | `audit_records` | 1 year |
| ScyllaDB | `device_fingerprints` | Indefinite (no TTL) |
| ClickHouse | `transactions_analytics` | 24 months |
| Kafka | `fraud.decisions` | 30 days |
| Kafka | `fraud.alerts` | 90 days |
