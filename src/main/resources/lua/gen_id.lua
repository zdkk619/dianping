-- gen_id.lua
-- KEYS[1] = 业务 key 前缀，例如 "id:order"
-- ARGV[1] = 起始时间戳（秒）
-- ARGV[2] = key 的过期时间（秒），防止 key 无限增长

local prefix = KEYS[1]
local start_timestamp = tonumber(ARGV[1])
local expire_time = tonumber(ARGV[2])

local now = redis.call('TIME') -- 返回 {秒, 微秒}
local now_timestamp = tonumber(now[1])
local timestamp = now_timestamp - start_timestamp

local sequence_key = prefix .. ':' .. timestamp
local count = redis.call('INCR', sequence_key)
redis.call('EXPIRE', sequence_key, expire_time)


return {timestamp, count}
