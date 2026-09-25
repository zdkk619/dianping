package com.zdkk.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.Shop;
import com.zdkk.mapper.ShopMapper;
import com.zdkk.service.IShopService;
import com.zdkk.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    @Override
    public Result queryById(Long id) {
        String json = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(json)) {
            return Result.ok(JSONUtil.toBean(json, Shop.class));
        }

        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                Expiration.from(RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES));
        return Result.ok(shop);
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
