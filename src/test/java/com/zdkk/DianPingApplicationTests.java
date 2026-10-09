package com.zdkk;

import com.zdkk.dto.Result;
import com.zdkk.entity.Shop;
import com.zdkk.service.IShopService;
import com.zdkk.service.impl.ShopServiceImpl;
import com.zdkk.utils.CacheClient;
import com.zdkk.utils.RedisConstants;
import com.zdkk.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
class DianPingApplicationTests {

    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopServiceImpl;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testLogicExpire() {
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1, shopServiceImpl.getById(1L), 20L, TimeUnit.SECONDS);
    }

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("test");
                System.out.println("id=" + id );
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time=" + (end - begin));
    }

    @Test
    public void testLoadShopData() {
        List<Shop> list = shopServiceImpl.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        map.forEach((typeId, shops) -> {
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = shops.stream().map(shop -> {
                return new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                );
            }).collect(Collectors.toList());
            stringRedisTemplate.opsForGeo().add(key, locations);
        });
    }

    @Test
    public void testHLL() {
        String[] users = new String[1000];
        String key = "hll";
        int index = 0;
        for (int i = 1; i <= 1000000; i++) {
            users[index++] = "user_" + i;
            if (i % 1000 == 0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add(key, users);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size(key);
        log.info("size = {}", size);
    }
}
