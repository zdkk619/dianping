package com.zdkk.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zdkk.dto.Result;
import com.zdkk.entity.Blog;
import com.zdkk.entity.Shop;
import com.zdkk.mapper.ShopMapper;
import com.zdkk.service.IShopService;
import com.zdkk.utils.CacheClient;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.RedisData;
import com.zdkk.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 使用互斥锁解决缓存击穿(包含缓存穿透)
        Shop shop = cacheClient.queryWithLock(RedisConstants.CACHE_SHOP_KEY, RedisConstants.LOCK_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 使用逻辑过期解决缓存击穿(假定所有数据已预热，不需要考虑缓存穿透)
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, RedisConstants.LOCK_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
        if (shop == null) return Result.fail("店铺不存在");
        else return Result.ok(shop);
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (typeId == null || current == null) {
            return Result.fail("参数错误");
        }
        // 1. 如果 x y 为空，按数据库查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 2. 如果 x y 不为空，按 Geo 查询(start, end]
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = start + SystemConstants.DEFAULT_PAGE_SIZE;
        // GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m ASC WITHDIST COUNT end
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(RedisConstants.SHOP_GEO_KEY + typeId,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        // 过滤出 start 到 end 的元素
        if (list.size() <= start) {
            return Result.ok(Collections.emptyList());
        }
        // 3. 如果 list 大小小于 start，返回空列表
        List<Long> ids = new ArrayList<>(list.size());
        // 4. 获取 list 中的 id
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(start).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, result.getDistance());
        });
        // 5. 根据 id 查询 Shop
        Map<Long, Shop> shopMap = query().in("id", ids).list()
                .stream().map(shop -> BeanUtil.copyProperties(shop, Shop.class))
                .collect(Collectors.toMap(Shop::getId, shop -> shop));
        // 6. 按 Geo查询的id 进行排序
        List<Shop> shopList = ids.stream().map(shopMap::get).toList();
        // 7. 填充距离
        shopList.forEach(shop -> {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        });
        return Result.ok(shopList);
    }
}
