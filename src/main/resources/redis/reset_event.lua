-- ============================
-- DEV ONLY: reset event state
-- ============================

-- KEYS
-- KEYS[1] = event:{eventId}:queue
-- KEYS[2] = event:{eventId}:ready
-- KEYS[3] = event:{eventId}:booking_active
-- KEYS[4] = active:queues
-- KEYS[5] = session prefix (e.g. "session:")

-- ARGV
-- ARGV[1] = eventId (string)

local queueKey = KEYS[1]
local readyKey = KEYS[2]
local bookingActiveKey = KEYS[3]
local activeQueuesKey = KEYS[4]
local sessionPrefix = KEYS[5]

local eventId = ARGV[1]

-- collect visitor tokens
local visitors = {}

local queueVisitors = redis.call("ZRANGE", queueKey, 0, -1)
for _, v in ipairs(queueVisitors) do
  visitors[v] = true
end

local readyVisitors = redis.call("ZRANGE", readyKey, 0, -1)
for _, v in ipairs(readyVisitors) do
  visitors[v] = true
end

-- delete sessions
local sessionDeleted = 0
for v, _ in pairs(visitors) do
  local sKey = sessionPrefix .. v
  if redis.call("DEL", sKey) == 1 then
    sessionDeleted = sessionDeleted + 1
  end
end

-- delete event keys
redis.call("DEL", queueKey)
redis.call("DEL", readyKey)
redis.call("DEL", bookingActiveKey)

-- remove from active queues
redis.call("SREM", activeQueuesKey, eventId)

-- return debug info
return {
  sessionDeleted,
  #queueVisitors,
  #readyVisitors
}
