-- serve_next_ready_zset.lua
-- Atomic serve NEXT READY visitor (FIFO)

-- KEYS:
-- 1 = readyZsetKey        (event:{id}:ready)
-- 2 = queueZsetKey        (event:{id}:queue)
-- 3 = bookingActiveKey   (event:{id}:booking_active)
-- 4 = activeQueueSet     (active:queues)
-- 5 = sessionPrefix      ("session:")

-- ARGV:
-- 1 = nowMillis
-- 2 = servedTTLSeconds
-- 3 = maxBooking
-- 4 = eventId

local now = tonumber(ARGV[1])
local ttl = tonumber(ARGV[2])
local maxBooking = tonumber(ARGV[3])

if not now or not ttl or not maxBooking then
    return { "ERROR", "INVALID_ARG" }
end

-- ===== 1. Cleanup expired active bookings =====
redis.call("ZREMRANGEBYSCORE", KEYS[3], "-inf", now)

-- ===== 2. Check booking limit =====
local active = redis.call("ZCARD", KEYS[3])
if active >= maxBooking then
    return { "LIMIT_REACHED" }
end

-- ===== 3. Pop next READY visitor (FIFO) =====
local popped = redis.call("ZPOPMIN", KEYS[1], 1)
if not popped or #popped == 0 then
    return { "NO_READY" }
end

local visitorToken = popped[1]
local sessionKey = KEYS[5] .. visitorToken

-- ===== 4. Validate session =====
if redis.call("EXISTS", sessionKey) == 0 then
    -- READY ghost → bỏ qua (KHÔNG cleanup queue)
    return { "SKIP_GHOST" }
end

-- still in main queue?
if redis.call("ZSCORE", KEYS[2], visitorToken) == false then
    return { "SKIP_NOT_IN_QUEUE" }
end

-- ===== 5. Serve this visitor =====
redis.call("ZREM", KEYS[2], visitorToken)

redis.call("HSET", sessionKey,
    "status", "SERVED",
    "servedAt", now
)
redis.call("EXPIRE", sessionKey, ttl)

local expireAt = now + ttl * 1000
redis.call("ZADD", KEYS[3], expireAt, visitorToken)

-- ensure event marked active
redis.call("SADD", KEYS[4], ARGV[4])

return { "OK", visitorToken }
