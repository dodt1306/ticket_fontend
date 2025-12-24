-- cleanup_queue.lua
-- Remove visitors in queue/ready whose session expired

-- KEYS:
-- 1 = queueZsetKey
-- 2 = readyZsetKey
-- 3 = sessionPrefix

-- ARGV:
-- 1 = batchSize

local batchSize = tonumber(ARGV[1]) or 200

local visitors = redis.call("ZRANGE", KEYS[1], 0, batchSize - 1)
local removed = 0

for i = 1, #visitors do
    local v = visitors[i]
    local sessionKey = KEYS[3] .. v

    if redis.call("EXISTS", sessionKey) == 0 then
        redis.call("ZREM", KEYS[1], v)
        redis.call("ZREM", KEYS[2], v)
        removed = removed + 1
    end
end

return removed
