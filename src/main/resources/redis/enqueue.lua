-- enqueue.lua (ACTIVE QUEUE ENABLED)

-- KEYS:
-- 1 = counterKey
-- 2 = queueKey
-- 3 = sessionKey
-- 4 = activeQueueSet        -- 🔥 NEW

-- ARGV:
-- 1 = visitorToken
-- 2 = eventId
-- 3 = now
-- 4 = sessionTTL

-- ===== 1. Nếu session tồn tại =====
if redis.call("EXISTS", KEYS[3]) == 1 then
    local seq = redis.call("HGET", KEYS[3], "sequence")

    -- Session hỏng → xoá và tạo lại
    if not seq then
        redis.call("DEL", KEYS[3])
    else
        redis.call("EXPIRE", KEYS[3], ARGV[4])
        return { "EXISTS", seq }
    end
end

-- ===== 2. Tạo sequence mới =====
local seq = redis.call("INCR", KEYS[1])

redis.call("ZADD", KEYS[2], seq, ARGV[1])

redis.call("HSET", KEYS[3],
    "eventId", ARGV[2],
    "queueTimestamp", ARGV[3],
    "status", "WAITING",
    "sequence", seq
)

redis.call("EXPIRE", KEYS[3], ARGV[4])

-- 🔥 ===== 3. MARK ACTIVE QUEUE =====
redis.call("SADD", KEYS[4], ARGV[2])

return { "OK", seq }
