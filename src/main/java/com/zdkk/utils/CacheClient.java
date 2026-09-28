package com.zdkk.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 解锁 Lua 脚本（校验持有者，原子删除）
    private static final String UNLOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param key 缓存键
     * @param value 缓存值
     * @param time 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), Expiration.from(time, unit));
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置 逻辑 TTL过期时间
     * @param key 缓存键
     * @param value 缓存值
     * @param time 过期时间
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix 缓存键的前缀
     * @param id 缓存键的后缀
     * @param type 反序列化后的类型
     * @param dbFallback 数据库查询方法
     * @param time 缓存过期时间
     * @param unit 缓存过期时间单位
     * @return 查询结果
     * @param <R> 查询结果类型
     * @param <ID> 缓存键的后缀类型
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            return json.isEmpty() ? null : JSONUtil.toBean(json, type);
        }

        R data = dbFallback.apply(id);
        if (data == null) {
            this.set(key, "", RedisConstants.CACHE_NULL_TTL, unit);
        } else {
            this.set(key, data, time, unit);
        }
        return data;
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用逻辑过期解决缓存击穿问题
     * @param keyPrefix 缓存键的前缀
     * @param lockKeyPrefix 缓存锁的前缀
     * @param id 缓存键的后缀
     * @param type 反序列化后的类型
     * @param dbFallback 数据库查询方法
     * @param time 缓存过期时间
     * @param unit 缓存过期时间单位
     * @return 查询结果
     * @param <R> 查询结果类型
     * @param <ID> 缓存键的后缀类型
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R data = JSONUtil.toBean((JSONUtil.toJsonStr(redisData.getData())), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return data;
        }
        String lockKey = lockKeyPrefix + id;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = tryLock(lockKey, lockValue);
        if (locked) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    String newJson = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(newJson)) {
                        RedisData newData = JSONUtil.toBean(newJson, RedisData.class);
                        if (newData.getExpireTime().isAfter(LocalDateTime.now())) {
                            return;
                        }
                    }
                    R apply = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, apply, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey, lockValue);
                }
            });
        }

        return data;
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用互斥锁解决缓存击穿问题
     * @param keyPrefix 缓存键的前缀
     * @param lockKeyPrefix 缓存锁的前缀
     * @param id 缓存键的后缀
     * @param type 反序列化后的类型
     * @param dbFallback 数据库查询方法
     * @param time 缓存过期时间
     * @param unit 缓存过期时间单位
     * @return 查询结果
     * @param <R> 查询结果类型
     * @param <ID> 缓存键的后缀类型
     */
    public <R, ID> R queryWithLock(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = lockKeyPrefix + id;
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return json.isEmpty() ? null : JSONUtil.toBean(json, type);
            }

            String lockValue = UUID.randomUUID().toString();
            boolean locked = tryLock(lockKey, lockValue);
            if (locked) {
                try {
                    String newJson = stringRedisTemplate.opsForValue().get(key);
                    if (newJson != null) {
                        return newJson.isEmpty() ? null : JSONUtil.toBean(newJson, type);
                    }
                    R data = dbFallback.apply(id);
                    this.set(key, data, time, unit);
                    return data;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey, lockValue);
                }
            } else {
                try {
                    Thread.sleep(50 + ThreadLocalRandom.current().nextInt(50));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

    private boolean tryLock(String lockKey, String lockValue) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Expiration.from(10L, TimeUnit.SECONDS));
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
    }
}
