package com.zdkk.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.Shop;
import com.zdkk.mapper.ShopMapper;
import com.zdkk.service.IShopService;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author zdkk
 * @since 2026-09-23
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 解锁 Lua 脚本（校验持有者，原子删除）
    private static final String UNLOCK_LUA = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    // 线程池：用于异步重建缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 解决缓存击穿(包含缓存穿透)
//        Shop shop = queryWithLock(id);

        Shop shop = queryWithLogicExpire(id);
        if (shop == null) return Result.fail("店铺不存在");
        else return Result.ok(shop);
    }

    private Shop queryWithLogicExpire(Long id) {
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从缓存中查询店铺
        String json = stringRedisTemplate.opsForValue().get(cacheKey);

        // 1.1 缓存未命中
        if (StrUtil.isBlank(json)) {
            // 1.2 逻辑过期方案通常配合"预热"使用，缓存里应该一直有数据，没有数据说明数据库里也没有，直接返回null
            return null;
        }

        // 2. 缓存命中，判断是否过期
        RedisData data = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) data.getData(), Shop.class);
        if (data.getExpireTime().isAfter(LocalDateTime.now())) {
            // 3 未过期，返回店铺信息
            return shop;
        }

        // 4. 已过期，获取锁后重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String lockValue = UUID.randomUUID().toString();
        boolean isLocked = tryLock(lockKey, lockValue);
        if (isLocked) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 双重检查
                    String newJson = stringRedisTemplate.opsForValue().get(cacheKey);
                    if (StrUtil.isNotBlank(newJson)) {
                        RedisData newData = JSONUtil.toBean(newJson, RedisData.class);
                        // 已重建，退出
                        if (newData.getExpireTime().isAfter(LocalDateTime.now())) {
                            return;
                        }
                    }
                    saveShopToRedis(id, 30L);
                } catch (Exception e) {
                    log.info("缓存重建失败 {}", id, e);
                } finally {
                    unlock(lockKey, lockValue);
                }
            });
        }
        return shop;
    }

    public void saveShopToRedis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private Shop queryWithLock(Long id) {
        String lock = RedisConstants.LOCK_SHOP_KEY + id;
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            // 1. 查缓存，缓存中有直接返回
            String json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if (json != null) {
                return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, Shop.class);
            }
            // 2. 缓存中没有，尝试获取锁，查询数据库并更新缓存
            String lockValue = UUID.randomUUID().toString();
            boolean locked = tryLock(lock, lockValue);
            // 2.1 获取锁成功，查询数据库
            if (locked) {
                try {
                    // 3. 双重检查，确保缓存未命中时才进行数据库查询
                    json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
                    if (json != null) {
                        return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, Shop.class);
                    }
                    // 4. 查询数据库并更新缓存
                    Shop shop = getById(id);
                    if (shop == null) {
                        // 将空值存入redis
                        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                                Expiration.from(RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES));
                        return null;
                    }
                    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                            Expiration.from(RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES));
                    return shop;
                } finally {
                    unlock(lock, lockValue);
                }
            } else {
                // 2.2 获取锁失败，等待重试
                try {
                    Thread.sleep(50 + ThreadLocalRandom.current().nextInt(50));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
        // 3. 重试次数耗尽，返回失败
        return null;
    }

    private Shop queryWithPassThrough(Long id) {
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (json != null) {
            return StrUtil.isBlank(json) ? null : JSONUtil.toBean(json, Shop.class);
        }

        Shop shop = getById(id);
        if (shop == null) {
            // 将空值存入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",
                    Expiration.from(RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES));
            return null;
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                Expiration.from(RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES));
        return shop;
    }

    private boolean tryLock(String key, String lockValue) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, lockValue, Expiration.from(RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS));
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key, String lockValue) {
        stringRedisTemplate.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                Collections.singletonList(key), lockValue);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        updateById(shop);

        // 2. 删除缓存
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.info("删除缓存 {}", RedisConstants.CACHE_SHOP_KEY + shopId);
                        stringRedisTemplate.delete(
                                RedisConstants.CACHE_SHOP_KEY + shopId);
                    }
                }
        );

        return Result.ok();
    }
}
