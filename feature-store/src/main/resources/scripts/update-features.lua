-- update-features.lua
--
-- Atomically updates all sliding-window features for a card.
--
-- KEYS[1] = features:{cardId}   Redis hash storing aggregated fields
-- KEYS[2] = txset:{cardId}      Sorted set of transaction timestamps (score = ms epoch)
-- KEYS[3] = merchants:{cardId}  HyperLogLog for distinct merchant tracking
--
-- ARGV[1] = timestamp (ms epoch)
-- ARGV[2] = amount (decimal string)
-- ARGV[3] = merchantId
-- ARGV[4] = country (2-letter ISO or empty)
-- ARGV[5] = deviceHash (or empty)
--
-- TTLs:
--   txset key     → 48 h  (172800 s) – covers 24 h window with headroom
--   merchants key → 24 h  (86400 s)
--   features hash → 7 d   (604800 s)
--
-- Returns: JSON string with all computed feature values

local features_key  = KEYS[1]
local txset_key     = KEYS[2]
local merchants_key = KEYS[3]

local now_ms    = tonumber(ARGV[1])
local amount    = tonumber(ARGV[2])
local merchant  = ARGV[3]
local country   = ARGV[4]
local device    = ARGV[5]

-- -----------------------------------------------------------------------
-- 1. Sliding-window transaction counts
-- -----------------------------------------------------------------------
-- Add this transaction to the sorted set
redis.call('ZADD', txset_key, now_ms, now_ms .. '-' .. merchant)

-- Remove entries older than 24 hours
local cutoff_24h = now_ms - 86400000
redis.call('ZREMRANGEBYSCORE', txset_key, '-inf', cutoff_24h)

-- Count entries in each window
local cutoff_1h = now_ms - 3600000
local cutoff_6h = now_ms - 21600000

local tx_count_1h  = redis.call('ZCOUNT', txset_key, cutoff_1h, now_ms)
local tx_count_6h  = redis.call('ZCOUNT', txset_key, cutoff_6h, now_ms)
local tx_count_24h = redis.call('ZCOUNT', txset_key, cutoff_24h, now_ms)

-- -----------------------------------------------------------------------
-- 2. Running average amount over 7 days
-- -----------------------------------------------------------------------
-- We store a running sum and count in the hash to avoid full scans.
-- 7-day window is approximated by the hash TTL; for strict windowing
-- a separate sorted set can be added in a future iteration.
local amt_sum   = tonumber(redis.call('HGET', features_key, 'amt_sum_7d')   or 0)
local amt_count = tonumber(redis.call('HGET', features_key, 'amt_count_7d') or 0)

amt_sum   = (amt_sum   or 0) + amount
amt_count = (amt_count or 0) + 1

local avg_amount_7d = amt_sum / amt_count

redis.call('HSET', features_key, 'amt_sum_7d',   amt_sum)
redis.call('HSET', features_key, 'amt_count_7d', amt_count)

-- -----------------------------------------------------------------------
-- 3. Distinct merchants in 24 h (HyperLogLog)
-- -----------------------------------------------------------------------
redis.call('PFADD', merchants_key, merchant)
local distinct_merchants_24h = redis.call('PFCOUNT', merchants_key)

-- -----------------------------------------------------------------------
-- 4. Country change detection
-- -----------------------------------------------------------------------
local prev_country     = redis.call('HGET', features_key, 'last_country') or ''
local country_changed  = 0
if prev_country ~= '' and prev_country ~= country then
    country_changed = 1
end

-- -----------------------------------------------------------------------
-- 5. Time since last transaction
-- -----------------------------------------------------------------------
local last_tx_ms_raw    = redis.call('HGET', features_key, 'last_tx_ms')
local time_since_last_ms = -1
if last_tx_ms_raw then
    local last_tx_ms = tonumber(last_tx_ms_raw)
    if last_tx_ms then
        time_since_last_ms = now_ms - last_tx_ms
    end
end

-- -----------------------------------------------------------------------
-- 6. Velocity score – simple normalised composite (0.0 – 1.0)
--    Weights can be tuned via configuration in a future iteration.
-- -----------------------------------------------------------------------
local velocity_score = 0.0
if tx_count_1h > 0 then
    -- Component scores, each capped at 1.0
    local freq_score  = math.min(tx_count_1h  / 10.0, 1.0)   -- 10+ tx/h = max
    local burst_score = math.min(tx_count_6h  / 20.0, 1.0)   -- 20+ tx/6h = max
    local ctry_score  = country_changed                        -- binary flag
    velocity_score = (0.5 * freq_score) + (0.3 * burst_score) + (0.2 * ctry_score)
end

-- -----------------------------------------------------------------------
-- 7. Persist computed features and metadata in the hash
-- -----------------------------------------------------------------------
redis.call('HSET', features_key,
    'tx_count_1h',             tx_count_1h,
    'tx_count_6h',             tx_count_6h,
    'tx_count_24h',            tx_count_24h,
    'avg_amount_7d',           avg_amount_7d,
    'distinct_merchants_24h',  distinct_merchants_24h,
    'last_country',            country,
    'country_changed',         country_changed,
    'time_since_last_tx_ms',   time_since_last_ms,
    'device_hash',             device,
    'velocity_score',          velocity_score,
    'last_tx_ms',              now_ms
)

-- -----------------------------------------------------------------------
-- 8. Set TTLs
-- -----------------------------------------------------------------------
redis.call('EXPIRE', txset_key,     172800)   -- 48 h
redis.call('EXPIRE', merchants_key, 86400)    -- 24 h
redis.call('EXPIRE', features_key,  604800)   -- 7 d

-- -----------------------------------------------------------------------
-- 9. Return JSON representation of updated features
-- -----------------------------------------------------------------------
return '{'
    .. '"tx_count_1h":'            .. tx_count_1h             .. ','
    .. '"tx_count_6h":'            .. tx_count_6h             .. ','
    .. '"tx_count_24h":'           .. tx_count_24h            .. ','
    .. '"avg_amount_7d":'          .. avg_amount_7d           .. ','
    .. '"distinct_merchants_24h":' .. distinct_merchants_24h  .. ','
    .. '"last_country":"'          .. country                 .. '",'
    .. '"country_changed":'        .. country_changed         .. ','
    .. '"time_since_last_tx_ms":'  .. time_since_last_ms      .. ','
    .. '"device_hash":"'           .. device                  .. '",'
    .. '"velocity_score":'         .. velocity_score
    .. '}'
