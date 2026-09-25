package com.zdkk.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.ShopType;
import com.zdkk.mapper.ShopTypeMapper;
import com.zdkk.service.IShopTypeService;
import com.zdkk.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        // 1. 先查缓存
        String cache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        // 2. 缓存未命中，先加载数据再重写缓存
        if (StrUtil.isBlank(cache)) {
            cache = loadAndCacheShopTypes();
        }

        return Result.ok(JSONUtil.parseArray(cache));
    }

    private String loadAndCacheShopTypes() {
        // 缓存未命中，查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        String json = CollUtil.isEmpty(typeList) ? "[]" : JSONUtil.toJsonStr(typeList);
        long ttl = CollUtil.isEmpty(typeList) ? RedisConstants.CACHE_NULL_TTL : RedisConstants.CACHE_SHOP_TYPE_TTL;
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, json, Expiration.from(ttl, TimeUnit.MINUTES));
        log.info("Cache miss, loaded and cached shop types {}", json);
        return json;
    }
}
