-- 1. 键
local seckill = KEYS[1]
local seckill_user = KEYS[2]

-- 用户ID
local user_id = ARGV[1]
-- 抢券时间
local time = ARGV[2]

if tonumber(redis.call('hget', seckill, 'stock')) <= 0 then
    return 1
end

if redis.call('hget', seckill, 'beginTime') > time then
    return 2
end

if redis.call('hget', seckill, 'endTime') < time then
    return 2
end

if redis.call('sismember', seckill_user, user_id) == 1 then
    return 3
end

redis.call('hincrby', seckill, 'stock', -1)

redis.call('sadd', seckill_user, user_id)

return 0
