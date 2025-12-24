-- check_and_mark_jti.lua
-- KEYS[1] = session key (e.g. session:{visitorToken})
-- ARGV[1] = expected jti
-- ARGV[2] = now timestamp (ISO)

local cur = redis.call("HGET", KEYS[1], "access_jti")
if not cur then
  return 2
end
if cur == ARGV[1] then
  local used = redis.call("HGET", KEYS[1], "access_used")
  if used == "1" then
    return 3
  end
  redis.call("HSET", KEYS[1], "access_used", "1")
  redis.call("HSET", KEYS[1], "access_used_at", ARGV[2])
  return 1
end
return 0
